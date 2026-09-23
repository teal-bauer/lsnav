package dev.ligustah.lsnav

import android.net.Uri
import android.util.Patterns

object MapsIntentParser {

    fun extractCoordinates(uri: Uri): Coordinates? {
        // .../maps/search/?query=52.52,13.41
        uri.getQueryParameter("query")?.let { query ->
            val parts = query.split(",")
            if (parts.size >= 2) {
                val lat = parts[0].toDoubleOrNull()
                val lon = parts[1].toDoubleOrNull()
                if (lat != null && lon != null) return Coordinates(lat, lon)
            }
        }

        // Route waypoints use 1d=longitude, 2d=latitude. The last pair is the destination.
        uri.pathSegments.find { it.startsWith("data=") }?.let { dataSegment ->
            if ("dir" in uri.pathSegments) {
                val waypoint = Regex("!1d(-?\\d+(?:\\.\\d+)?)!2d(-?\\d+(?:\\.\\d+)?)")
                    .findAll(dataSegment).mapNotNull { match ->
                        val lon = match.groupValues[1].toDoubleOrNull()
                        val lat = match.groupValues[2].toDoubleOrNull()
                        if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
                            Coordinates(lat, lon)
                        } else null
                    }.lastOrNull()
                if (waypoint != null) return waypoint
            }

            val rootNode = parseGoogleMapsData(dataSegment)
            if (rootNode != null) {
                val coords = findCoordinatesInTree(rootNode)
                if (coords != null) return coords
            }
        }

        // The @ coordinates describe the map viewport, not necessarily the destination.
        uri.pathSegments.find { it.startsWith("@") }?.let { segment ->
            val parts = segment.removePrefix("@").split(",")
            if (parts.size >= 2) {
                val lat = parts[0].toDoubleOrNull()
                val lon = parts[1].toDoubleOrNull()
                if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) return Coordinates(lat, lon)
            }
        }

        val q = uri.getQueryParameter("q")
        if (q != null) {
            val coords = q.split(",")
            val lat = coords.getOrNull(0)?.toDoubleOrNull()
            val lon = coords.getOrNull(1)?.toDoubleOrNull()
            if (lat != null && lon != null) return Coordinates(lat, lon)
        }

        // .../maps/place/52.441733,13.418110/...
        uri.pathSegments.forEach { segment ->
            val parts = segment.split(",")
            if (parts.size == 2) {
                val lat = parts[0].toDoubleOrNull()
                val lon = parts[1].toDoubleOrNull()
                if (lat != null && lon != null && lat >= -90.0 && lat <= 90.0 && lon >= -180.0 && lon <= 180.0) {
                    return Coordinates(lat, lon)
                }
            }
        }

        return null
    }
    
    class PbNode(val id: Int, val type: Char, val value: String) {
        val children = mutableListOf<PbNode>()
        var parent: PbNode? = null

        fun getTotalDescendantCount(): Int {
            var count = children.size
            for (child in children) {
                count += child.getTotalDescendantCount()
            }
            return count
        }

        fun findLatestIncompleteNode(): PbNode {
            if ((type == 'm' && (value.toIntOrNull() ?: 0) > getTotalDescendantCount()) || parent == null) {
                return this
            }
            return parent!!.findLatestIncompleteNode()
        }
    }

    private fun parseGoogleMapsData(dataStr: String): PbNode? {
        val root = PbNode(0, 'r', "")
        var currentNode = root
        val elements = dataStr.removePrefix("data=").split("!").filter { it.isNotEmpty() }
        
        for (elem in elements) {
            var i = 0
            while (i < elem.length && elem[i].isDigit()) {
                i++
            }
            if (i > 0 && i < elem.length) {
                val idStr = elem.substring(0, i)
                val typeChar = elem[i]
                val valueStr = elem.substring(i + 1)
                
                val id = idStr.toIntOrNull()
                if (id != null) {
                    val node = PbNode(id, typeChar, valueStr)
                    node.parent = currentNode
                    currentNode.children.add(node)
                    
                    currentNode = node.findLatestIncompleteNode()
                }
            }
        }
        return root
    }

    private fun findCoordinatesInTree(node: PbNode): Coordinates? {
        // Look for typical Map Pins where 3d = Lat, 4d = Lon
        val lat3d = node.children.find { it.id == 3 && it.type == 'd' }
        val lon4d = node.children.find { it.id == 4 && it.type == 'd' }
        if (lat3d != null && lon4d != null) {
            val lat = lat3d.value.toDoubleOrNull()
            val lon = lon4d.value.toDoubleOrNull()
            if (lat != null && lon != null && lat >= -90.0 && lat <= 90.0 && lon >= -180.0 && lon <= 180.0) {
                return Coordinates(lat, lon)
            }
        }

        // Look for Route destinations where 2d = Lat, 1d = Lon
        val lon1d = node.children.find { it.id == 1 && it.type == 'd' }
        val lat2d = node.children.find { it.id == 2 && it.type == 'd' }
        if (lat2d != null && lon1d != null) {
            val lat = lat2d.value.toDoubleOrNull()
            val lon = lon1d.value.toDoubleOrNull()
            if (lat != null && lon != null && lat >= -90.0 && lat <= 90.0 && lon >= -180.0 && lon <= 180.0) {
                return Coordinates(lat, lon)
            }
        }

        // Deep Search (Reverse so we find the destination waypoints typically at the end of the arrays)
        for (child in node.children.reversed()) {
            val res = findCoordinatesInTree(child)
            if (res != null) return res
        }
        return null
    }

    fun extractPlaceName(uri: Uri): String? {
        val pathSegments = uri.pathSegments
        val placeIndex = pathSegments.indexOf("place")
        if (placeIndex != -1 && placeIndex + 1 < pathSegments.size) {
            val nameSegment = pathSegments[placeIndex + 1]
            val parts = nameSegment.split(",")
            val isJustCoords = parts.size == 2 && parts[0].toDoubleOrNull() != null && parts[1].toDoubleOrNull() != null
            if (!isJustCoords) {
                return java.net.URLDecoder.decode(nameSegment.replace("+", " "), "UTF-8")
            }
        }
        
        uri.getQueryParameter("q")?.let { q ->
            val parts = q.split(",")
            val isJustCoords = parts.size == 2 && parts[0].toDoubleOrNull() != null && parts[1].toDoubleOrNull() != null
            if (!isJustCoords) {
                return java.net.URLDecoder.decode(q.replace("+", " "), "UTF-8")
            }
        }
        
        return null
    }

    fun extractUrlOrText(text: String): String {
        val matcher = android.util.Patterns.WEB_URL.matcher(text)
        if (matcher.find()) {
            return matcher.group() ?: text
        }
        return text
    }
}

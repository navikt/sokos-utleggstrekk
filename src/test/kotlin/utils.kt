fun getResourceAsText(file: String): String? =
    object {}.javaClass.getResource(file)?.readText()
package me.berezovskyi.corsherdf.corsherdfweb.util

class HtmlReturnedException(val uri: String) : RuntimeException("Server returned HTML instead of RDF for URI: $uri")

package me.berezovskyi.corsherdf.corsherdfweb.mapper

import me.berezovskyi.corsherdf.corsherdfweb.util.HtmlReturnedException
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider

@Provider
class HtmlReturnedExceptionMapper : ExceptionMapper<HtmlReturnedException> {
    override fun toResponse(exception: HtmlReturnedException): Response {
        return Response.status(422) // Unprocessable Entity
            .entity("Server did not return any RDF, got HTML instead from ${exception.uri}")
            .type(MediaType.TEXT_PLAIN)
            .build()
    }
}

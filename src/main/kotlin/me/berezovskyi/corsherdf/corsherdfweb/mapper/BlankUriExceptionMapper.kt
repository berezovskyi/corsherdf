package me.berezovskyi.corsherdf.corsherdfweb.mapper

import me.berezovskyi.corsherdf.corsherdfweb.util.BlankUriException
import javax.ws.rs.core.Response
import javax.ws.rs.core.MediaType
import javax.ws.rs.ext.ExceptionMapper
import javax.ws.rs.ext.Provider

@Provider
class BlankUriExceptionMapper : ExceptionMapper<BlankUriException> {
    override fun toResponse(exception: BlankUriException): Response {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity("Pass the RDF document URI after /r/")
            .type(MediaType.TEXT_PLAIN)
            .build()
    }
}

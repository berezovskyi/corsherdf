/*
 * CORSher RDF is a proxy that allows to fetch RDF from badly configured servers
 * Copyright (C) 2020 Andrii Berezovskyi
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51 Franklin
 * Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package me.berezovskyi.corsherdf.corsherdfweb

// import org.apache.logging.log4j.util.Strings // Replaced with Kotlin's isBlank()
import org.slf4j.LoggerFactory
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient
import org.eclipse.microprofile.rest.client.inject.RestClient
import javax.inject.Inject
import javax.ws.rs.*
import javax.ws.rs.container.ContainerRequestContext
import javax.ws.rs.container.ContainerRequestFilter
import javax.ws.rs.container.ContainerResponseContext
import javax.ws.rs.container.ContainerResponseFilter
import javax.ws.rs.core.*
import javax.ws.rs.ext.Provider
import java.net.URI
import me.berezovskyi.corsherdf.corsherdfweb.util.BlankUriException
import me.berezovskyi.corsherdf.corsherdfweb.util.HtmlReturnedException


val logger = LoggerFactory.getLogger("CorsherdfWebApplication")

@Provider
class LoggingFilter : ContainerRequestFilter, ContainerResponseFilter {
    override fun filter(requestContext: ContainerRequestContext) {
        logger.info("Processing request method=${requestContext.method} path=${requestContext.uriInfo.path} params=[${requestContext.uriInfo.queryParameters}] headers=[${requestContext.headers}]")
    }

    override fun filter(requestContext: ContainerRequestContext, responseContext: ContainerResponseContext) {
        logger.info("Handling response for path=${requestContext.uriInfo.path} with status=${responseContext.statusInfo} headers=[${responseContext.headers}]")
    }
}

@Path("/r")
class RdfResource {

    @Inject
    @field:RestClient
    lateinit var rdfClient: RdfClient

    @GET
    @Path("/{uri:.*}") // :.* allows empty URI path param, e.g. for /r/
    @Produces(MediaType.WILDCARD) // Produces any type, will be set dynamically
    fun r(@Context requestContext: ContainerRequestContext, @PathParam("uri") remoteUriString: String, @Context httpHeaders: HttpHeaders): Response { // Removed suspend
        val uri_p = remoteUriString
        logger.info("Received remoteUriString: '$uri_p', isBlank: ${uri_p.isBlank()}") // More detailed logging
        // Logging of request is now handled by LoggingFilter

        if (uri_p.isBlank()) {
            logger.warn("uri_p is blank, throwing BlankUriException. Value: '$uri_p'")
            throw BlankUriException()
        }

        val acceptHeader = httpHeaders.getHeaderString(HttpHeaders.ACCEPT)
        if (acceptHeader?.contains("html", ignoreCase = true) == true ||
            acceptHeader?.contains("application/json", ignoreCase = true) == true // Assuming JSON is not an RDF format here
        ) {
            logger.warn("Accept header requests HTML or JSON, returning 406. Accept: $acceptHeader")
            // Keeping manual response for 406 as it's not covered by new mappers
            return Response.status(Response.Status.NOT_ACCEPTABLE)
                .entity("Only RDF formats can be requested")
                .type(MediaType.TEXT_PLAIN)
                .build()
        }

        val finalAccept = acceptHeader ?: "*/*"

        try {
            logger.info("Entering try block for URI: '$uri_p'. Final Accept: '$finalAccept'")
            val targetUri = URI.create(uri_p).toString()
            logger.info("Successfully created target URI: '$targetUri'")

            val responseFromRemote = kotlinx.coroutines.runBlocking { rdfClient.fetchRdf(targetUri, finalAccept) } // Added runBlocking
            logger.info("Response from remote for '$targetUri': Status=${responseFromRemote.status}, Content-Type='${responseFromRemote.getHeaderString("Content-Type")}'")


            val remoteContentType = responseFromRemote.getHeaderString("Content-Type") ?: MediaType.APPLICATION_OCTET_STREAM
            val remoteStatusCode = responseFromRemote.status

            val responseBuilder = Response.status(remoteStatusCode)
            responseBuilder.header("X-Content-Type", remoteContentType)
            responseBuilder.header("X-Status-Code", remoteStatusCode)

            if (remoteStatusCode >= 400) {
                logger.warn("Remote server returned error $remoteStatusCode for '$targetUri'. Returning 502.")
                return Response.status(Response.Status.BAD_GATEWAY)
                    .entity("Error fetching the resource from $uri_p. Status: $remoteStatusCode")
                    .type(MediaType.TEXT_PLAIN) // Using .type() as per instruction
                    .header("X-Content-Type", remoteContentType)
                    .header("X-Status-Code", remoteStatusCode)
                    .build()
            } else if (remoteContentType.contains("html", ignoreCase = true)) {
                logger.warn("Remote server returned HTML for '$targetUri'. Throwing HtmlReturnedException.")
                throw HtmlReturnedException(uri_p)
            } else {
                responseBuilder.type(remoteContentType) // Set the actual content type
                responseBuilder.entity(responseFromRemote.readEntity(ByteArray::class.java))
            }
            return responseBuilder.build()

        } catch (e: WebApplicationException) {
            logger.error("WebApplicationException from RDF client for '$uri_p': ${e.message}", e)
            val remoteResponse = e.response
            val entityMessage = try { remoteResponse?.readEntity(String::class.java) ?: e.message } catch (readEx: Exception) { e.message }
            return Response.status(Response.Status.BAD_GATEWAY)
                .entity("Failed to fetch from $uri_p. Client Error: $entityMessage. Remote status: ${remoteResponse?.statusInfo?.toEnum()?.statusCode ?: "N/A"}")
                .type(MediaType.TEXT_PLAIN) // Using .type() as per instruction
                .header("X-Content-Type", remoteResponse?.mediaType ?: MediaType.TEXT_PLAIN)
                .header("X-Status-Code", remoteResponse?.statusInfo?.toEnum()?.statusCode ?: 502)
                .build()
        } catch (e: IllegalArgumentException) {
            logger.error("Invalid URI syntax for '$uri_p': ${e.message}", e)
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("Invalid URI syntax provided for: '$uri_p'. Error: ${e.message}")
                .type(MediaType.TEXT_PLAIN) // Using .type() as per instruction
                .build()
        } catch (e: Exception) { // Catch all other exceptions
            logger.error("Generic Exception (e.g. network, processing) for '$uri_p': ${e.message}", e)
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("Error proxying the request to $uri_p. Message: ${e.message}")
                .type(MediaType.TEXT_PLAIN) // Using .type() as per instruction
                .build()
        }
    }
}

@RegisterRestClient(configKey="rdf-client")
interface RdfClient {
    @GET
    @Path("/{uri}")
    suspend fun fetchRdf(@PathParam("uri") uri: String, @HeaderParam(HttpHeaders.ACCEPT) accept: String): Response // Removed default User-Agent
}

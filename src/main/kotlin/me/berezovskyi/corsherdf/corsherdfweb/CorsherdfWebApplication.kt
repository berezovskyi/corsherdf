package me.berezovskyi.corsherdf.corsherdfweb

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.http.MediaType
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux
import reactor.netty.http.client.HttpClient
import java.util.function.BiPredicate

@SpringBootApplication
class CorsherdfWebApplication

fun main(args: Array<String>) {
    runApplication<CorsherdfWebApplication>(*args)
}

@RestController
class RestController() {
    @GetMapping(
        value = ["/r/**"],
        produces = [MediaType.TEXT_PLAIN_VALUE]
    )
    fun r(request: ServerHttpRequest, response: ServerHttpResponse): Flux<DataBuffer> {
        val uri_p = request.uri.path.substring(3) // skip /r/
        // todo 401 if there are any query params, the client failed to escape the uri correctly
        println(uri_p);

        val client = WebClient.builder().baseUrl(uri_p)
            .defaultHeader("Accept", "text/turtle,application/rdf+xml,application/ntriples,application/ld+json")
            .clientConnector(
                ReactorClientHttpConnector(
                    // need a predicate because 303 redirects are not followed by default
                    HttpClient.create().followRedirect(BiPredicate { req, resp ->
                        val statusCode = resp.status().code()
                        println("Redirect to ${resp.responseHeaders()["Location"]} / HTTP ${resp.status()}")
                        statusCode in 301..399 || resp.responseHeaders().contains("Location")
                    })
                )
            )
            .build();

        //TODO timeout
        return client.get().exchangeToFlux {
            val contentType: String = it.headers().header("Content-Type").first()
            println("${it.rawStatusCode()}/$contentType")

            response.headers["Access-Control-Allow-Origin"] = "*"
            response.headers["Access-Control-Allow-Methods"] = "GET, HEAD, OPTIONS"
            response.headers["Access-Control-Allow-Headers"] = "Accept, OSLC-Core-Version, Configuration-Context, OSLC-Configuration-Context"

            response.headers["X-Content-Type"] = contentType
            response.headers["X-Status-Code"] = it.rawStatusCode().toString()
            if (it.statusCode().isError) {
                response.rawStatusCode = 599
                Flux.just(DefaultDataBufferFactory.sharedInstance.wrap("Error fetching the resource".encodeToByteArray()))
            } else if (contentType.contains("html", ignoreCase = true)) {
                response.rawStatusCode = 422
                Flux.just(DefaultDataBufferFactory.sharedInstance.wrap("Server did not return any RDF".encodeToByteArray()))
            } else {
                it.bodyToFlux(DataBuffer::class.java)
            }
        }
//        return Flux.empty<DataBuffer>();
    }
}
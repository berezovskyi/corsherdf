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

import org.apache.logging.log4j.util.Strings
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.http.MediaType
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.server.WebFilter
import reactor.core.publisher.Flux
import reactor.netty.http.client.HttpClient

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import reactor.core.publisher.Mono

import org.springframework.http.HttpStatus
import org.springframework.web.cors.reactive.CorsUtils
import org.springframework.web.reactive.config.CorsRegistry
import org.springframework.web.reactive.config.EnableWebFlux
import org.springframework.web.reactive.config.WebFluxConfigurer
//import org.springframework.web.cors.reactive.CorsUtils

import org.springframework.web.server.WebFilterChain

import org.springframework.web.server.ServerWebExchange


val logger = LoggerFactory.getLogger(CorsherdfWebApplication::class.java)

@SpringBootApplication
class CorsherdfWebApplication

fun main(args: Array<String>) {
    runApplication<CorsherdfWebApplication>(*args)
}

//@Configuration
//class RequestLoggingFilterConfig {
//
//
//}

@Configuration
@EnableWebFlux
class WebConfig : WebFluxConfigurer {
    @Bean
    fun loggingFilter(): WebFilter =
        WebFilter { exchange, chain ->
            val request = exchange.request
            logger.info("Processing request method=${request.method} path=${request.path.pathWithinApplication()} params=[${request.queryParams}] body=[${request.body}]")
            val result = chain.filter(exchange)
            logger.info("Handling with response ${exchange.response}")
            return@WebFilter result
        }

    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/**")
            .allowedOriginPatterns("*") // any host or put domain(s) here
            .allowedMethods("GET", "HEAD", "OPTIONS") // put the http verbs you want allow
            .allowedHeaders("content-type", "oslc-core-version", "configuration-context", "oslc-configuration-context") // put the http headers you want allow
        //todo cache cors response
    }
}

@RestController
class RestController() {
    @RequestMapping("/r/**", method = [RequestMethod.GET, RequestMethod.HEAD])
    fun r(request: ServerHttpRequest, response: ServerHttpResponse): Flux<DataBuffer> {
        val uri_p = request.uri.path.substring(3) // skip /r/
        // todo 401 if there are any query params, the client failed to escape the uri correctly
        println(uri_p);

        if (Strings.isBlank(uri_p)) {
            response.rawStatusCode = 400;
            return fluxByteString("Pass the RDF document URI after /r/")
        }

        if (request.headers["Accept"]?.contains("html") == true
            || request.headers["Accept"]?.contains("application/json") == true
        ) {
            response.rawStatusCode = 400;
            return fluxByteString("Only RDF formats can be requested")
        }
        // TODO make (m AND mList) -> sort by priority ; also add low priorities for us
        val mediaTypes = MediaType.parseMediaTypes(request.headers["Accept"])

        // TODO allow "annoying" content negotiation by passing 1 Accept header at a time
//        mediaTypes.addAll(
//            listOf("text/turtle", "application/rdf+xml", "application/ntriples", "application/ld+json").map(
//                MediaType::parseMediaType
//            )
//        )
        // TODO consider Jena conversion on-the-fly

        // TODO: 2020-12-11 cache reponse
        val finalAccept = MediaType.toString(mediaTypes)
        logger.debug("Requesting RDF with this {Accept: {}}", finalAccept)
        val client = WebClient.builder().baseUrl(uri_p)
            .defaultHeader("Accept", finalAccept)
            .clientConnector(
                ReactorClientHttpConnector(
                    // need a predicate because 303 redirects are not followed by default
                    HttpClient.create().followRedirect { req, resp ->
                        val statusCode = resp.status().code()
                        println("Redirect to ${resp.responseHeaders()["Location"]} / HTTP ${resp.status()}")
                        statusCode in 301..399 || resp.responseHeaders().contains("Location")
                    }
                )
            )
            .build();

        //TODO timeout
        return client.get().exchangeToFlux {
            val contentType: String = it.headers().header("Content-Type").first()
            println("${it.rawStatusCode()}/$contentType")


            response.headers["Content-Type"] = "text/plain"
            response.headers["X-Content-Type"] = contentType
            response.headers["X-Status-Code"] = it.rawStatusCode().toString()
            if (it.statusCode().isError) {
                response.rawStatusCode = 599
                fluxByteString("Error fetching the resource")
            } else if (contentType.contains("html", ignoreCase = true)) {
                response.rawStatusCode = 422
                fluxByteString("Server did not return any RDF")
            } else {
                response.headers["Content-Type"] = contentType
                it.bodyToFlux(DataBuffer::class.java)
            }
        }
    }

    private fun fluxByteString(s: String): Flux<DataBuffer> =
        Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(s.encodeToByteArray()))
}
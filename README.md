# CORSher RDF proxy

Proxy server that allows you to request RDF within web browser code from servers that do not comply with basic needs of Linked Data apps.

## Introduction

Using server-side proxies is not the best approach but in many cases is not avoidable. This is especially the case when you are building a Linked Data client that works in the web browser or has hypermedia "behaviour" (i.e. not centralised). This project allows you to retry a failed request with a proxy that will:

- ✅ Add CORS headers (read-only and only for public resources).
- 🚧 Convert the RDF response if a response did not match your desired `Accept` type.
- 🚧 Caching for resources that resolved with a 200 code up to 24h before. Endpoint uptime is a known problem in the semantic web / linked data community.
- 🚧 Speed up traversal of RDF resources across globally distributed origins (self-hosted).
- 🚧 Serving of the resources that are no longer on the web or cannot be dereferenced but have a prominent place in the LOD. Most likely start is the list of all recognised prefixes under http://prefix.cc/ (with a likely blacklist for PURL and W3ID as they have a valid process for updating redirects that shall be followed instead)

## Roadmap

- [ ] Shall we respect robots.txt? I am with Archive.org on this one, we don't have to if we are not an _automatic_ bot but only make requests on behalf of users
- [ ] Add `Forwarded: for=` and `X-Forwared-For:` headers, we are a white proxy
- [ ] Do Jena model conversion on the fly if the server failed to return highest-q format.
- [ ] Add caching (considering MapDB, RocksDB, ChronicleMap for now)
- [ ] Deploy a public instance (fallback only, not for speedup)
- [ ] Write a spec (essentially `/r/escapeUri($host$path)escapeUriComponents($query)` plus a table of all HTTP codes)
- [ ] Add a process for defining sideloading of RDF documents.
  - [ ] via https://prefix.cc/
  - [ ] via https://lov.linkeddata.es/dataset/lov/
  - [ ] via https://archivo.dbpedia.org/ and potentially `dev` stage ontologies (e.g. via `X-Corsherdf-Dev: allow`)

## Spec

Requests shall be made to `/r/escapeUri($host$path)escapeUriComponents($query)` with any supported RDF mime types in the `Accept` header.

CORS headers allowed:

- `accept` (so that RDF types can be specified)
- `oslc-core-version`, `configuration-context`, `oslc-configuration-context` (widely used OSLC REST headers but not registered with IANA)

Extra headers added:

- X-Content-Type
- X-Status-Code

Bot user agent:

> Mozilla/4.0 (compatible; CORSheRDF/0.1; +https://github.com/berezovskyi/corsherdf)

Error codes:

- 400: no URI given or bad URI is given
- 405: only GET or HEAD methods are allowed (except for the CORS preflight requests)
- 406: non-RDF resource requested
- 422: origin returned non-RDF response
- 429: you are overloading the server, add delays
- 502: origin returned a 4xx/5xx response
- 503: there are too many requests in the queue, retry soon
- 504: origin timeout

Example:

```
> GET /r/http://open-services.net/ns/cm%3Fc%3D202012112354%23 HTTP/1.1
> Host: localhost:8080
> Origin: http://localhost:3000
> Connection: keep-alive
> Accept: text/turtle
> Access-Control-Request-Method: GET
> Access-Control-Request-Headers: oslc-core-version
> Origin: http://localhost:3000
> Sec-Fetch-Mode: cors
> Sec-Fetch-Site: same-site
> Sec-Fetch-Dest: empty
> Referer: http://localhost:3000/
> Accept-Encoding: gzip, deflate, br
> Accept-Language: en

< HTTP/1.1 200 OK
< transfer-encoding: chunked
< Vary: Origin
< Vary: Access-Control-Request-Method
< Vary: Access-Control-Request-Headers
< Access-Control-Allow-Origin: http://localhost:3000
< X-Content-Type: text/turtle; charset=UTF-8
< X-Status-Code: 200
< Content-Type: text/turtle; charset=UTF-8
```

Also, for the failing request:

```
< HTTP/1.1 502 Bad Gateway
< transfer-encoding: chunked
< Vary: Origin
< Vary: Access-Control-Request-Method
< Vary: Access-Control-Request-Headers
< Access-Control-Allow-Origin: http://localhost:3000
< Content-Type: text/plain
< X-Content-Type: text/html; charset=utf-8
< X-Status-Code: 404
```
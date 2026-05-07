package dev.gisketch.chowkingdom.discord

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import dev.gisketch.chowkingdom.ChowKingdomMod
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.Executors

object DiscordQuickSkinAvatarServer {
    private var server: HttpServer? = null
    private var bindKey: String = ""

    fun reload(config: DiscordWebhookConfig) {
        val serverConfig = config.quickSkinAvatarServer
        val nextBindKey = "${serverConfig.bindHost}:${serverConfig.port}"
        if (!config.enabled || !serverConfig.enabled) {
            stop()
            return
        }
        if (server != null && bindKey == nextBindKey) return
        stop()
        start(config)
    }

    fun start(config: DiscordWebhookConfig) {
        val serverConfig = config.quickSkinAvatarServer
        if (!config.enabled || !serverConfig.enabled) return
        if (server != null) return

        val address = InetSocketAddress(serverConfig.bindHost, serverConfig.port)
        val startedServer = runCatching { HttpServer.create(address, 0) }.getOrElse { exception ->
            ChowKingdomMod.LOGGER.warn("Failed to start Quick Skin avatar server on {}:{}", serverConfig.bindHost, serverConfig.port, exception)
            return
        }

        startedServer.createContext("/quickskin/avatar") { exchange -> handleAvatarRequest(exchange) }
        startedServer.createContext("/npc/avatar") { exchange -> handleNpcAvatarRequest(exchange) }
        startedServer.executor = Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "chowkingdom-discord-avatar").apply { isDaemon = true }
        }
        startedServer.start()
        server = startedServer
        bindKey = "${serverConfig.bindHost}:${serverConfig.port}"
        ChowKingdomMod.LOGGER.info("Started Quick Skin avatar server on {}:{}", serverConfig.bindHost, serverConfig.port)
    }

    private fun stop() {
        server?.stop(0)
        server = null
        bindKey = ""
    }

    fun diagnostics(config: DiscordWebhookConfig): List<String> {
        val serverConfig = config.quickSkinAvatarServer
        val localHost = if (serverConfig.bindHost == "0.0.0.0") "127.0.0.1" else serverConfig.bindHost
        return listOf(
            "Discord Quick Skin avatar server",
            "enabled=${serverConfig.enabled}",
            "running=${server != null}",
            "bind=$bindKey",
            "local_base_url=http://$localHost:${serverConfig.port}",
            "public_base_url=${serverConfig.publicBaseUrl.ifBlank { "missing" }}",
            "cloudflare_command=cloudflared tunnel --url http://127.0.0.1:${serverConfig.port}",
            "set public_base_url to the https://*.trycloudflare.com URL, then restart Minecraft",
        )
    }

    private fun handleAvatarRequest(exchange: HttpExchange) {
        runCatching {
            if (exchange.requestMethod != "GET") {
                exchange.sendPlain(405, "method not allowed")
                return
            }

            val uuid = requestUuid(exchange.requestURI.path) ?: run {
                exchange.sendPlain(400, "bad uuid")
                return
            }
            val result = DiscordQuickSkinSupport.quickSkinHeadResult(uuid, queryParameter(exchange.requestURI.rawQuery, "skin") ?: queryParameter(exchange.requestURI.rawQuery, "skin_id"))
            val image = result.image ?: run {
                exchange.sendPlain(404, result.reason)
                return
            }

            exchange.responseHeaders.add("Content-Type", "image/png")
            exchange.responseHeaders.add("Cache-Control", "no-cache")
            exchange.sendResponseHeaders(200, image.size.toLong())
            exchange.responseBody.use { output -> output.write(image) }
        }.onFailure { exception ->
            ChowKingdomMod.LOGGER.warn("Quick Skin avatar request failed", exception)
            runCatching { exchange.sendPlain(500, "server error") }
        }.also {
            exchange.close()
        }
    }

    private fun handleNpcAvatarRequest(exchange: HttpExchange) {
        runCatching {
            if (exchange.requestMethod != "GET") {
                exchange.sendPlain(405, "method not allowed")
                return
            }
            val npcId = requestNpcId(exchange.requestURI.path) ?: run {
                exchange.sendPlain(400, "bad npc")
                return
            }
            val image = DiscordQuickSkinSupport.npcHeadPng(npcId) ?: run {
                exchange.sendPlain(404, "missing npc avatar")
                return
            }
            exchange.responseHeaders.add("Content-Type", "image/png")
            exchange.responseHeaders.add("Cache-Control", "no-cache")
            exchange.sendResponseHeaders(200, image.size.toLong())
            exchange.responseBody.use { output -> output.write(image) }
        }.onFailure { exception ->
            ChowKingdomMod.LOGGER.warn("NPC avatar request failed", exception)
            runCatching { exchange.sendPlain(500, "server error") }
        }.also {
            exchange.close()
        }
    }

    private fun requestUuid(path: String): UUID? {
        val raw = path.removePrefix("/quickskin/avatar/").removeSuffix(".png")
        return runCatching { UUID.fromString(raw) }.getOrNull()
    }

    private fun requestNpcId(path: String): String? = URLDecoder.decode(path.removePrefix("/npc/avatar/").removeSuffix(".png"), StandardCharsets.UTF_8)
        .trim()
        .takeIf { value -> value.matches(Regex("[a-z0-9_\\-]{1,64}")) }

    private fun queryParameter(rawQuery: String?, key: String): String? = rawQuery
        ?.split('&')
        ?.firstNotNullOfOrNull { pair ->
            val name = pair.substringBefore('=')
            if (name != key) return@firstNotNullOfOrNull null
            URLDecoder.decode(pair.substringAfter('=', ""), StandardCharsets.UTF_8).takeIf(String::isNotBlank)
        }

    private fun HttpExchange.sendPlain(status: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        responseHeaders.add("Content-Type", "text/plain; charset=utf-8")
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.use { output -> output.write(bytes) }
    }
}
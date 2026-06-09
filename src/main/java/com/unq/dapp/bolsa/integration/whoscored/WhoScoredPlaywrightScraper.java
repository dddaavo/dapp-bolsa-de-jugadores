package com.unq.dapp.bolsa.integration.whoscored;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitUntilState;
import com.unq.dapp.bolsa.catalog.domain.League;
import com.unq.dapp.bolsa.catalog.domain.Position;
import com.unq.dapp.bolsa.integration.port.ScrapedPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scraper de WhoScored usando Playwright (Chromium headless).
 * <p>
 * WhoScored renderiza sus tablas con JavaScript y usa Cloudflare como anti-bot,
 * por lo que Jsoup (HTTP plano) no funciona — se necesita un browser real.
 * <p>
 * <p>
 * Técnicas anti-detección aplicadas:
 * <ul>
 *   <li>User-Agent de Chrome real</li>
 *   <li>Flag {@code navigator.webdriver} ocultado vía initScript</li>
 *   <li>Args de Chromium que desactivan la marca de automatización</li>
 *   <li>Delay antes de extraer datos (simula lectura humana)</li>
 * </ul>
 */
@Component
public class WhoScoredPlaywrightScraper implements WhoScoredScraper {

    private static final Logger log = LoggerFactory.getLogger(WhoScoredPlaywrightScraper.class);

    /**
     * URLs de la tabla de estadísticas de jugadores por liga en WhoScored.
     * El parámetro "stageId" apunta a la temporada actual — revisar anualmente.
     * Formato: .../Seasons/{seasonId}/Stages/{stageId}/PlayerStatistics
     */
    private static final Map<League, String> LEAGUE_STATS_URLS = Map.of(
            League.PREMIER_LEAGUE,
            "https://www.whoscored.com/Regions/252/Tournaments/2/Seasons/9618/Stages/22076/PlayerStatistics",
            League.LA_LIGA,
            "https://www.whoscored.com/Regions/206/Tournaments/4/Seasons/9650/Stages/22149/PlayerStatistics",
            League.BUNDESLIGA,
            "https://www.whoscored.com/Regions/81/Tournaments/3/Seasons/9617/Stages/22075/PlayerStatistics",
            League.SERIE_A,
            "https://www.whoscored.com/Regions/108/Tournaments/5/Seasons/9637/Stages/22108/PlayerStatistics",
            League.LIGUE_1,
            "https://www.whoscored.com/Regions/74/Tournaments/22/Seasons/9644/Stages/22134/PlayerStatistics"
    );

    /** Máximos jugadores a extraer por liga para no saturar. */
    private static final int MAX_PLAYERS_PER_LEAGUE = 20;

    /**
     * Rutas donde Chrome/Chromium suele estar instalado según el SO.
     * Playwright usa estas rutas cuando se quiere usar el browser del sistema
     * en lugar del Chromium bundleado por Playwright.
     */
    private static final List<String> CHROME_PATHS = List.of(
            // Linux
            "/usr/bin/google-chrome",
            "/usr/bin/google-chrome-stable",
            "/usr/bin/chromium-browser",
            "/usr/bin/chromium",
            "/snap/bin/chromium",
            // macOS
            "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
            "/Applications/Chromium.app/Contents/MacOS/Chromium",
            // Windows
            "C:/Program Files/Google/Chrome/Application/chrome.exe",
            "C:/Program Files (x86)/Google/Chrome/Application/chrome.exe",
            System.getProperty("user.home") + "/AppData/Local/Google/Chrome/Application/chrome.exe",
            "C:/Program Files/Chromium/Application/chrome.exe",
            "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe",
            "C:/Program Files/Microsoft/Edge/Application/msedge.exe"
    );

    public List<ScrapedPlayer> fetchPlayersByLeague(League league) {
        String url = LEAGUE_STATS_URLS.get(league);
        log.info("[WhoScored] Iniciando scraping de {} — url: {}", league, url);

        /*
         * Playwright Java inicia un proceso hijo Node.js para descargar/gestionar browsers.
         * En redes con proxy SSL inspection (certificado autofirmado), ese proceso Node
         * falla con SELF_SIGNED_CERT_IN_CHAIN.
         *
         * Solución: NODE_TLS_REJECT_UNAUTHORIZED=0 debe estar en el entorno del proceso JVM
         * ANTES de llamar a Playwright.create().
         *
         * Lo intentamos inyectar via reflexión (best-effort). Si falla por módulo restringido,
         * el usuario debe establecer la variable externamente (ver log de error abajo).
         */
        tryInjectNodeTlsBypass();

        List<ScrapedPlayer> result = new ArrayList<>();

        try (Playwright playwright = Playwright.create()) {

            Optional<Path> chromePath = findInstalledChrome();
            if (chromePath.isEmpty()) {
                log.error("[WhoScored] No se encontró Chrome/Edge instalado. Instalá Google Chrome o configurá CHROME_EXECUTABLE_PATH.");
                return result;
            }
            log.info("[WhoScored] Usando browser en: {}", chromePath.get());

            BrowserType.LaunchOptions launchOpts = new BrowserType.LaunchOptions()
                    .setExecutablePath(chromePath.get())
                    .setHeadless(true)
                    .setArgs(List.of(
                            "--disable-blink-features=AutomationControlled",
                            "--no-sandbox",
                            "--disable-dev-shm-usage",
                            "--disable-gpu"
                    ));

            Browser.NewContextOptions contextOptions = new Browser.NewContextOptions()
                    .setUserAgent("""
                            Mozilla/5.0 (Windows NT 10.0; Win64; x64) \
                            AppleWebKit/537.36 (KHTML, like Gecko) \
                            Chrome/124.0.0.0 Safari/537.36""")
                    .setViewportSize(1280, 800)
                    .setLocale("en-US");

            try (Browser browser = playwright.chromium().launch(launchOpts);
                 BrowserContext context = browser.newContext(contextOptions);
                 Page page = context.newPage()) {

                // Ocultar el flag que delata al headless browser
                page.addInitScript(
                        "Object.defineProperty(navigator, 'webdriver', {get: () => undefined});"
                );

                // Primero ir a la home para obtener cookies y parecer un usuario real
                page.navigate("https://www.whoscored.com",
                        new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
                page.waitForTimeout(2000);

                // Navegar a la página de estadísticas de la liga
                page.navigate(url,
                        new Page.NavigateOptions()
                                .setWaitUntil(WaitUntilState.NETWORKIDLE)
                                .setTimeout(45_000));

                // Esperar a que la tabla principal esté presente
                page.waitForSelector("#player-table-statistics-body",
                        new Page.WaitForSelectorOptions().setTimeout(20_000));

                List<ElementHandle> rows = page.querySelectorAll(
                        "#player-table-statistics-body tr"
                );
                log.info("[WhoScored] {} filas encontradas para {}", rows.size(), league);

                for (int i = 0; i < Math.min(rows.size(), MAX_PLAYERS_PER_LEAGUE); i++) {
                    parseRowSafe(rows.get(i), league, i, result);
                }

                log.info("[WhoScored] Scraping completado para {}: {} jugadores extraídos",
                        league, result.size());
            }
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("SELF_SIGNED_CERT")) {
                log.error("""
                        [WhoScored] ERROR DE PROXY SSL — Playwright no puede descargar Chromium. \
                        Ejecutá la app con la variable de entorno NODE_TLS_REJECT_UNAUTHORIZED=0:
                          PowerShell: $env:NODE_TLS_REJECT_UNAUTHORIZED = '0'
                          CMD:        set NODE_TLS_REJECT_UNAUTHORIZED=0
                        y luego corré de nuevo: .\\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local""");
            } else {
                log.error("[WhoScored] Scraping falló para {}: {}", league, e.getMessage());
            }
        }

        return result;
    }

    /**
     * Intenta inyectar NODE_TLS_REJECT_UNAUTHORIZED=0 en el entorno del proceso JVM
     * para que sea heredado por el proceso Node.js interno de Playwright.
     *
     * Requiere que la JVM se inicie con:
     *   --add-opens java.base/java.lang=ALL-UNNAMED
     * El pom.xml ya lo configura para spring-boot:run y los tests.
     * En IntelliJ: Run Configuration → VM options → pegar ese flag.
     */
    @SuppressWarnings({"unchecked", "java:S3011"})
    private void tryInjectNodeTlsBypass() {
        if ("0".equals(System.getenv("NODE_TLS_REJECT_UNAUTHORIZED"))) {
            return; // Ya está seteado externamente
        }
        try {
            // Acceder directamente al map mutable interno de ProcessEnvironment
            Class<?> peClass = Class.forName("java.lang.ProcessEnvironment");
            Field theEnvField = peClass.getDeclaredField("theEnvironment");
            theEnvField.setAccessible(true);
            Map<String, String> env = (Map<String, String>) theEnvField.get(null);
            env.put("NODE_TLS_REJECT_UNAUTHORIZED", "0");
            log.info("[WhoScored] NODE_TLS_REJECT_UNAUTHORIZED=0 inyectado via reflexión.");
        } catch (Exception e) {
            log.warn("""
                    [WhoScored] Reflexión falló ({}). \
                    Asegurate de que la JVM tenga: --add-opens java.base/java.lang=ALL-UNNAMED
                      — En IntelliJ: Run Configuration → Modify options → VM options → agregar ese flag
                      — O seteá la variable en Run Configuration → Environment variables: NODE_TLS_REJECT_UNAUTHORIZED=0""",
                    e.getMessage());
        }
    }

    /**
     * Busca un ejecutable de Chrome o Edge instalado en el sistema.
     * También respeta la variable de entorno {@code CHROME_EXECUTABLE_PATH}
     * para que pueda sobreescribirse sin tocar código.
     */
    private Optional<Path> findInstalledChrome() {
        // Variable de entorno tiene prioridad
        String envPath = System.getenv("CHROME_EXECUTABLE_PATH");
        if (envPath != null && !envPath.isBlank()) {
            Path p = Paths.get(envPath);
            if (p.toFile().exists()) {
                return Optional.of(p);
            }
            log.warn("[WhoScored] CHROME_EXECUTABLE_PATH apunta a un archivo que no existe: {}", envPath);
        }

        return CHROME_PATHS.stream()
                .map(Paths::get)
                .filter(p -> p.toFile().exists())
                .findFirst();
    }

    /**
     * Parsea una fila {@code <tr>} de la tabla de jugadores de WhoScored.
     *
     * Estructura actual del DOM (puede cambiar con temporadas):
     * - El ID del jugador está en el href del player-link: /players/{id}/show/{slug}
     * - El nombre está en span.iconize dentro del a.player-link del td.overflow-text
     * - El equipo está en span.team-name (con coma al final que se elimina)
     * - La posición está en el último span.player-meta-data, formato: ",  D(L),M(CLR)  "
     * - La fila duplicada (td.grid-ghost-cell) se ignora acotando queries a td.overflow-text
     */
    private static final Pattern PLAYER_ID_PATTERN = Pattern.compile("/players/(\\d+)/");

    private void parseRowSafe(ElementHandle row, League league, int index, List<ScrapedPlayer> result) {
        try {
            ScrapedPlayer player = parseRow(row, league);
            if (player != null) result.add(player);
        } catch (Exception e) {
            log.warn("[WhoScored] Error parseando fila {}: {}", index, e.getMessage());
        }
    }

    private ScrapedPlayer parseRow(ElementHandle row, League league) {
        ElementHandle mainTd = row.querySelector("td.overflow-text");
        if (mainTd == null) return null;

        ElementHandle playerLink = mainTd.querySelector("a.player-link");
        if (playerLink == null) return null;

        String href = playerLink.getAttribute("href");
        if (href == null || href.isBlank()) return null;
        Matcher m = PLAYER_ID_PATTERN.matcher(href);
        String whoScoredId = m.find() ? m.group(1) : "";

        String name = "";
        ElementHandle nameSpan = playerLink.querySelector("span.iconize");
        if (nameSpan != null) {
            name = nameSpan.innerText().trim();
        }
        if (name.isEmpty()) return null;

        String team = "";
        ElementHandle teamSpan = mainTd.querySelector("span.team-name");
        if (teamSpan != null) {
            team = teamSpan.innerText().trim().replaceAll(",\\s*$", "");
        }

        // Último span.player-meta-data contiene la posición: ",  D(L),M(CLR)  "
        String posText = "";
        List<ElementHandle> metaSpans = mainTd.querySelectorAll("span.player-meta-data");
        if (!metaSpans.isEmpty()) {
            String raw = metaSpans.get(metaSpans.size() - 1).innerText().trim();
            posText = raw.replaceAll("^[,\\s]+", "").trim();
        }

        return new ScrapedPlayer(whoScoredId, name, team, mapPosition(posText), league, "");
    }

    /**
     * Mapea el código de posición de WhoScored al enum {@link Position}.
     *
     * WhoScored ahora usa formatos con paréntesis y múltiples posiciones:
     * "D(L),M(CLR)", "AM(CLR),FW", "DMC", "FW", "GK"
     * Se toma la primera posición y se elimina el sufijo entre paréntesis.
     * DMC (defensive mid) se verifica antes de D(efender) para evitar mismatch.
     */
    private Position mapPosition(String posCode) {
        if (posCode == null || posCode.isEmpty()) return Position.FW;
        String first = posCode.split(",")[0].trim();
        String base = first.replaceAll("\\([^)]*\\)", "").trim().toUpperCase();

        if (base.equals("GK"))                                             return Position.GK;
        if (base.startsWith("DM") || base.startsWith("M") || base.startsWith("AM")) return Position.MF;
        if (base.startsWith("D") || base.equals("SW"))                    return Position.DF;
        return Position.FW;
    }
}


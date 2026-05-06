package com.unq.dapp.bolsa.integration.whoscored;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitUntilState;
import com.unq.dapp.bolsa.catalog.domain.League;
import com.unq.dapp.bolsa.catalog.domain.Position;
import com.unq.dapp.bolsa.integration.port.PlayerStatsPort;
import com.unq.dapp.bolsa.integration.port.ScrapedPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Scraper de WhoScored usando Playwright (Chromium headless).
 * <p>
 * WhoScored renderiza sus tablas con JavaScript y usa Cloudflare como anti-bot,
 * por lo que Jsoup (HTTP plano) no funciona — se necesita un browser real.
 * <p>
 * Esta clase NO lleva @Component directamente: la instancia {@link WhoScoredAdapter}
 * (que sí es @Component) decide cuándo invocarla según la propiedad
 * {@code whoscored.scraping.enabled}.
 * <p>
 * Técnicas anti-detección aplicadas:
 * <ul>
 *   <li>User-Agent de Chrome real</li>
 *   <li>Flag {@code navigator.webdriver} ocultado vía initScript</li>
 *   <li>Args de Chromium que desactivan la marca de automatización</li>
 *   <li>Delay antes de extraer datos (simula lectura humana)</li>
 * </ul>
 */
public class WhoScoredPlaywrightScraper implements PlayerStatsPort {

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
     * Rutas donde Chrome suele estar instalado en Windows.
     * Playwright usa estas rutas cuando PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD está activo
     * o cuando la descarga de Chromium falla por un proxy corporativo con SSL inspection.
     */
    private static final List<String> CHROME_PATHS = List.of(
            "C:/Program Files/Google/Chrome/Application/chrome.exe",
            "C:/Program Files (x86)/Google/Chrome/Application/chrome.exe",
            System.getProperty("user.home") + "/AppData/Local/Google/Chrome/Application/chrome.exe",
            // Chromium alternativo
            "C:/Program Files/Chromium/Application/chrome.exe",
            // Edge (basado en Chromium, funciona con Playwright)
            "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe",
            "C:/Program Files/Microsoft/Edge/Application/msedge.exe"
    );

    @Override
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
                log.error("[WhoScored] No se encontró Chrome/Edge instalado. "
                        + "Instalá Google Chrome o configurá CHROME_EXECUTABLE_PATH.");
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

            try (Browser browser = playwright.chromium().launch(launchOpts)) {

                BrowserContext context = browser.newContext(
                        new Browser.NewContextOptions()
                                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                                        + "AppleWebKit/537.36 (KHTML, like Gecko) "
                                        + "Chrome/124.0.0.0 Safari/537.36")
                                .setViewportSize(1280, 800)
                                .setLocale("en-US")
                );

                Page page = context.newPage();

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

                // Pausa breve para que terminen de cargar los datos de la tabla
                page.waitForTimeout(3000);

                List<ElementHandle> rows = page.querySelectorAll(
                        "#player-table-statistics-body tr.player-table-statistics"
                );
                log.info("[WhoScored] {} filas encontradas para {}", rows.size(), league);

                for (int i = 0; i < Math.min(rows.size(), MAX_PLAYERS_PER_LEAGUE); i++) {
                    try {
                        ScrapedPlayer player = parseRow(rows.get(i), league);
                        if (player != null) result.add(player);
                    } catch (Exception e) {
                        log.warn("[WhoScored] Error parseando fila {}: {}", i, e.getMessage());
                    }
                }

                log.info("[WhoScored] Scraping completado para {}: {} jugadores extraídos",
                        league, result.size());
            }
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("SELF_SIGNED_CERT")) {
                log.error("[WhoScored] ERROR DE PROXY SSL — Playwright no puede descargar Chromium. "
                        + "Ejecutá la app con la variable de entorno NODE_TLS_REJECT_UNAUTHORIZED=0:\n"
                        + "  PowerShell: $env:NODE_TLS_REJECT_UNAUTHORIZED = '0'\n"
                        + "  CMD:        set NODE_TLS_REJECT_UNAUTHORIZED=0\n"
                        + "y luego corré de nuevo: .\\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local");
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
    @SuppressWarnings("unchecked")
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
            log.warn("[WhoScored] Reflexión falló ({}). "
                    + "Asegurate de que la JVM tenga: --add-opens java.base/java.lang=ALL-UNNAMED\n"
                    + "  — En IntelliJ: Run Configuration → Modify options → VM options → agregar ese flag\n"
                    + "  — O seteá la variable en Run Configuration → Environment variables: NODE_TLS_REJECT_UNAUTHORIZED=0",
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
     * <p>Estructura esperada de la fila (simplificada):
     * <pre>
     *   td.pn   → número de posición en el ranking
     *   td.pi   → contiene <a class="player-link"> con el nombre
     *   td.tname → contiene <a class="team-link"> con el equipo
     *   td.pos  → posición abreviada (FW, MF, DF, GK)
     *   data-player-id → ID interno de WhoScored
     * </pre>
     */
    private ScrapedPlayer parseRow(ElementHandle row, League league) {
        // ID interno de WhoScored
        String whoScoredId = row.getAttribute("data-player-id");

        // Nombre del jugador
        ElementHandle nameEl = row.querySelector("td.pn a.player-link");
        if (nameEl == null) return null;
        String name = nameEl.innerText().trim();

        // Equipo
        String team = "";
        ElementHandle teamEl = row.querySelector("td.tname a.team-link");
        if (teamEl != null) {
            team = teamEl.innerText().trim();
        }

        // Posición (WhoScored usa: FW, AM, MF, DF, GK, etc.)
        String posText = "";
        ElementHandle posEl = row.querySelector("td.pos");
        if (posEl != null) {
            posText = posEl.innerText().trim();
        }

        return new ScrapedPlayer(whoScoredId, name, team, mapPosition(posText), league, "");
    }

    /**
     * Mapea el código de posición de WhoScored al enum {@link Position}.
     * WhoScored puede devolver: GK, DC, DR, DL (defensas), MC, ML, MR, AMC, AML, AMR, FW, SS
     */
    private Position mapPosition(String posCode) {
        if (posCode == null || posCode.isEmpty()) return Position.FW;
        String pos = posCode.toUpperCase();
        if (pos.startsWith("GK"))                       return Position.GK;
        if (pos.startsWith("D") || pos.startsWith("SW")) return Position.DF;
        if (pos.startsWith("M") || pos.startsWith("AM")) return Position.MF;
        return Position.FW; // FW, SS, AML sobre la línea atacante
    }
}


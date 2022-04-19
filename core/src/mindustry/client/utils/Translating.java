package mindustry.client.utils;

import arc.func.*;
import arc.struct.*;
import arc.util.*;
import arc.util.Http.*;
import arc.util.serialization.JsonWriter.*;
import mindustry.client.*;
import mindustry.io.*;

/** Partial wrapper for the <a href=https://libretranslate.com>LibreTranslate API</a>
 * <!-- Is this how I'm supposed to write async --->
 * @author Weathercold
 */
public class Translating {
    /** List of mirrors can be found <a href=https://github.com/LibreTranslate/LibreTranslate#mirrors>here</a>.
     * If you see a mirror not working, please make a pr.
     */
    public static volatile ObjectMap<String, Boolean> servers = ObjectMap.of(
        //"libretranslate.com", false, requires API key :(
        "translate.argosopentech.com", false,
        "translate.api.skitzen.com", false,
        "libretranslate.de", false,
        "libretranslate.pussthecat.org", false,
        "translate.fortytwo-it.com", false
    );

    // Might break certain mods idk
    static {JsonIO.json.setOutputType(OutputType.json);}


    /** Retrieve an array of supported languages.
     * @param success The callback to run if no errors occurred.
     */
    public static void languages(Cons<Seq<String>> success) {
        fetch(
            "/languages",
            res -> {
                StringMap[] langs = JsonIO.json.fromJson(StringMap[].class, res);
                Seq<String> codes = new Seq<>(langs.length);
                for (StringMap lang : langs) codes.add(lang.get("code"));
                success.get(codes);
            }
        );
    }

    /** Get the language of the specified text.
     * @param success The callback to run if no errors occurred.
     */
    public static void detect(String text, Cons<String> success) {
        if (text == null) {
            Log.err(new NullPointerException("Detect text cannot be null."));
            return;
        }

        fetch(
            "/detect",
            StringMap.of("q", text),
            res -> success.get(JsonIO.json.fromJson(StringMap[].class, res)[0].get("language"))
        );
    }

    /** Detect source then translate */
    public static void translate(String text, String target, Cons<String> success) {
        translate(text, "auto", target, success);
    }

    /** Translate the specified text from the source language to the target language.
     * @param source Source language code.
     * @param target target language code.
     * @param success The callback to run if no errors occurred.
     */
    public static void translate(String text, String source, String target, Cons<String> success) {
        if (text == null || source == null || target == null) {
            Log.err(new NullPointerException("Translate arguments cannot be null."));
            return;
        }
        if (source == target) {success.get(text); return;}

        fetch(
            "/translate",
            StringMap.of(
                "q", text,
                "source", source,
                "target", target
            ),
            res -> success.get(JsonIO.json.fromJson(StringMap.class, res).get("translatedText"))
        );
    }

    private static void fetch(String api, Cons<String> success) {
        fetch(api, HttpMethod.GET, null, success);
    }
    private static void fetch(String api, @Nullable StringMap body, Cons<String> success) {
        fetch(api, HttpMethod.POST, body, success);
    }

    private static void fetch(String api, HttpMethod method, @Nullable StringMap body, Cons<String> success) {
        String server = servers.findKey(false, false);
        if (server == null) {
            Log.warn("Rate limit reached on all servers. Aborting translation.");
            return;
        }

        Http.post("https://" + server + api)
            .method(method)
            .header("Content-Type", "application/json")
            .content(JsonIO.json.toJson(body, StringMap.class, String.class))
            .error(e -> {
                if (e instanceof HttpStatusException) {
                    HttpStatusException hse = (HttpStatusException) e;
                    switch (hse.status) {
                        case UNKNOWN_STATUS: // rate limit
                            Log.info("Rate limit reached with @, retrying...", server + api);
                            servers.put(server, true);
                            Timer.schedule(() -> servers.put(server, false), 60f);
                            fetch(api, body, success);
                            break;
                        case BAD_REQUEST:
                            Log.warn("Bad request, aborting translation.\n@", body);
                            break;
                        case INTERNAL_SERVER_ERROR:
                            Log.warn("Server-side error, aborting translation.\n@", body);
                            break;
                        default:
                            if (servers.size >= 2) {
                                Log.warn("HTTP Response indicates error, retrying...\n@", hse);
                                servers.remove(server);
                                fetch(api, body, success);
                            } else {
                                Log.err("HTTP Response indicates error, disabling translation for this session", hse);
                                ClientVars.enableTranslation = false;
                            }
                    }
                } else {
                    Log.err("An unknown error occurred, disabling translation for this session", e);
                    ClientVars.enableTranslation = false;
                }
            })
            .submit(response -> {
                String result = response.getResultAsString();
                Log.debug("Response from @:\n@", server, result.replace("\n", ""));
                success.get(result);
            });
    }
}
package mindustry.client.utils;

import java.io.*;

import arc.func.*;
import arc.struct.*;
import arc.util.*;
import arc.util.Http.*;
import arc.util.serialization.JsonWriter.*;
import mindustry.client.*;
import mindustry.io.*;

/** Partial wrapper for the <a href="https://libretranslate.com">LibreTranslate API</a><p>
 * Technically this falls into the <a href="https://github.com/LibreTranslate/LibreTranslate#can-i-use-your-api-server-at-libretranslatecom-for-my-application-in-production">"infrequent use"</a> category (I hope so)<p>
 * <!-- Is this how I'm supposed to write async --->
 * @author Weathercold
 */
public class Translating{
    public static Seq<String> servers = Seq.with(
        //"libretranslate.com", requires API key :(
        "translate.argosopentech.com",
        "translate.api.skitzen.com",
        "trans.zillyhuhn.com",
        "translate.mentality.rip" //sus link
    );

    //Might break certain mods idk
    static {JsonIO.json.setOutputType(OutputType.json);}

    /** Get the language of the specified text, then run success if no errors occurred.
     * @param success The callback to run if no errors occurred.
    */
    public static void detect(String text, Cons<String> success) {
        if (text == null) {
            Log.err(new NullPointerException("Detect text cannot be null."));
            return;
        }

        buildSend(
            "/detect",
            "{\"q\":\"" + text + "\"}",
            res -> success.get(JsonIO.json.fromJson(StringMap[].class, res)[0].get("language"))
        );
    }

    /** Retrieve an array of supported languages, then run success if no errors occurred.
     * @param success The callback to run if no errors occurred.
     */
    public static void languages(Cons<Seq<String>> success) {
        buildSend(
            "/languages",
            "", //no body
            res -> {
                StringMap[] langs = JsonIO.json.fromJson(StringMap[].class, res);
                Seq<String> codes = new Seq<>(langs.length);
                for (StringMap lang : langs) codes.add(lang.get("code"));
                success.get(codes);
            }
        );
    }

    /** detect() + translate() */
    public static void translate(String text, String target, Cons<String> success) {
        detect(text, source -> translate(text, source, target, success));
    }

    /** Translate the specified text from the source language to the target language, then run success if no errors occurred.
     * @param source Language code of the source language.
     * @param target Language code of the target language.
     * @param success The callback to run if no errors occurred.
     */
    public static void translate(String text, String source, String target, Cons<String> success) {
        if (text == null || source == null || target == null) {
            Log.err(new NullPointerException("Translate arguments cannot be null."));
            return;
        }
        if (source == target) {success.get(text); return;}

        buildSend(
            "/translate",
            JsonIO.json.toJson(StringMap.of(
                "q", text,
                "source", source,
                "target", target
            ), StringMap.class, String.class),
            res -> success.get(JsonIO.json.fromJson(StringMap.class, res).get("translatedText"))
        );
    }

    private static void buildSend(String api, String content, Cons<String> success) {
        ConsT<HttpResponse, Exception> successWrap = res -> {
            String cont = res.getResultAsString();
            Log.debug("Response from @:[]\n@", servers.first(), cont.replace("\n", ""));
            success.get(cont);
        };
        HttpRequest request = Http.post("https://" + servers.first() + api)
                                  .header("Content-Type", "application/json")
                                  .content(content);
        request.error(e -> {
            if (e instanceof HttpStatusException && servers.size >= 2) {
                HttpStatusException hse = (HttpStatusException)e;
                Log.warn("Response from @ indicates error (@ @), retrying with @:[]\n@",
                         servers.remove(0) + api, hse.status.code, hse.status, servers.first(), hse.response.getResultAsString().replace("\n", ""));
                request.url("https://" + servers.first() + api).submit(successWrap);
            }
            else if (e instanceof HttpStatusException) {
                HttpStatusException hse = (HttpStatusException)e;
                Log.err("Response from @ indicates error (@ @), disabling translation for this session:[]\n@",
                        servers.first() + api, hse.status.code, hse.status, hse.response.getResultAsString().replace("\n", ""));
                ClientVars.enableTranslation = false;
            }
            else {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                e.printStackTrace(pw);
                Log.err("An unknown error occurred, disabling translation for this session:[]\n" + sw);
                ClientVars.enableTranslation = false;
            }
        }).submit(successWrap);
    }
}

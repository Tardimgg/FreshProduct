package com.example.freshproduct.productInfoLoader;

import static java.lang.System.exit;

import android.os.AsyncTask;
import android.text.Html;
import android.text.Spanned;
import android.util.Pair;
import android.widget.Toast;

import com.example.freshproduct.Result;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;
import java.util.zip.GZIPInputStream;

public class ProductInfoLoader extends AsyncTask<String, Integer, Result<Pair<String, String>, String>> {

    InfoDownloadCompleted completionListener;

    public ProductInfoLoader(InfoDownloadCompleted listener) {
        this.completionListener = listener;
    }

    @Override
    protected void onPostExecute(Result<Pair<String, String>, String> s) {
        super.onPostExecute(s);
        completionListener.event(s);
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
        super.onProgressUpdate(values);
    }

    @Override
    protected Result<Pair<String, String>, String> doInBackground(String... strings) {
        try {
            URL url = new URL("https://barcodes.olegon.ru/");
            HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
            httpConn.setRequestMethod("POST");

            httpConn.setRequestProperty("Host", "barcodes.olegon.ru");
//            httpConn.setRequestProperty("Content-Length", "15");
            httpConn.setRequestProperty("Cache-Control", "max-age=0");
            httpConn.setRequestProperty("Sec-Ch-Ua", "\" Not A;Brand\";v=\"99\", \"Chromium\";v=\"98\", \"Google Chrome\";v=\"98\"");
            httpConn.setRequestProperty("Sec-Ch-Ua-Mobile", "?0");
            httpConn.setRequestProperty("Sec-Ch-Ua-Platform", "\"Linux\"");
            httpConn.setRequestProperty("Upgrade-Insecure-Requests", "1");
            httpConn.setRequestProperty("Origin", "https://barcodes.olegon.ru");
            httpConn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            httpConn.setRequestProperty("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/98.0.4758.80 Safari/537.36");
            httpConn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9");
            httpConn.setRequestProperty("Sec-Fetch-Site", "same-origin");
            httpConn.setRequestProperty("Sec-Fetch-Mode", "navigate");
            httpConn.setRequestProperty("Sec-Fetch-User", "?1");
            httpConn.setRequestProperty("Sec-Fetch-Dest", "document");
            httpConn.setRequestProperty("Referer", "https://barcodes.olegon.ru/search.php");
            httpConn.setRequestProperty("Accept-Encoding", "gzip, deflate");
            httpConn.setRequestProperty("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7");

            httpConn.setDoOutput(true);
            OutputStreamWriter writer = new OutputStreamWriter(httpConn.getOutputStream());
            writer.write("b=");
            writer.write(strings[0]);
            writer.flush();
            writer.close();
            httpConn.getOutputStream().close();

            InputStream responseStream = httpConn.getResponseCode() / 100 == 2
                    ? httpConn.getInputStream()
                    : httpConn.getErrorStream();
            if ("gzip".equals(httpConn.getContentEncoding())) {
                responseStream = new GZIPInputStream(responseStream);
            }
            Scanner s = new Scanner(responseStream).useDelimiter("\\A");
            String response = s.hasNext() ? s.next() : "";

            Document document = Jsoup.parse(response);
            Element elem = document.body().getElementById("names");
            Element classification = document.body().getElementById("classif");

            if (elem != null && classification != null) {
                String[] resClassification = classification.text().split(", ");
                String[] res = elem.text().split(", ");
                if (res.length > 0) {
                    return Result.some(Pair.create(resClassification[0].substring(resClassification[0].indexOf(":") + 1),
                            res.length > 1 ? res[1] : res[0].substring(res[0].indexOf(":") + 1)));
                }
            } else if (response.equals("\nОчень быстрые запросы. Подождите, пожалуйста.")) {
                return Result.error("Сервер перегружен");
            }
            return Result.error("Неизвестный товар");

        } catch (IOException e) {
            e.printStackTrace();
            return Result.error("Проблемы со связью");
        }
    }
}


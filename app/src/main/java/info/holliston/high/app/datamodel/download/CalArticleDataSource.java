package info.holliston.high.app.datamodel.download;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import info.holliston.high.app.datamodel.Article;

/**
 * Created by Tom on 10/16/2015.
 */
public class CalArticleDataSource extends ArticleDataSource {

    public CalArticleDataSource(Context context, ArticleDataSourceOptions options) {
        this.setup(context, options);
    }

    @Override
    List<Article> downloadArticlesFromNetwork(ArticleParser.SourceMode refreshSource) {
        String result;
        List<Article> returnArticles = null;

        int articlesCount = this.getAllArticles().size();
        // if datasource is empty or downloading is OK...
        if ((refreshSource == ArticleParser.SourceMode.DOWNLOAD_ONLY)
                || (refreshSource == ArticleParser.SourceMode.PREFER_DOWNLOAD)
                || (articlesCount <= 0)) {
            InputStream stream = null;
            try {

                // Instantiate the parser
                EventJsonParser jsonParser = new EventJsonParser(options.getParserNames());

                // these JSON feeds require the addition of dates added to the feed URL
                Date now = new Date();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'00:00:00-05:00");
                String nowString = sdf.format(now);

                String urlString = options.getUrlString();
                urlString = urlString + "&timeMin=" + nowString;
                stream = downloadUrl(urlString);

                //download and parse
                String parseResult = jsonParser.parse(stream);
                result = "Downloaded: " + parseResult;
                returnArticles = jsonParser.getAllArticles();

            } catch (Exception e) {
                result = "Downloading error: " + e.toString() + ". Using cache instead.";
            } finally {
                try {
                    if (stream != null) {
                        stream.close();
                    }
                } catch (Exception e) {
                    //ignore
                }
            }
        } else {
            result = "Download skipped: " + articlesCount + " events in cache (good enough)";
        }
        return returnArticles;
    }
}
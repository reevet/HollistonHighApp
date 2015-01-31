package info.holliston.high.app;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.widget.SwipeRefreshLayout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ExpandableListView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import info.holliston.high.app.adapter.EventsArrayAdapter;
import info.holliston.high.app.model.Article;
import info.holliston.high.app.pager.EventPager;
import info.holliston.high.app.xmlparser.ArticleParser;

public class EventsListFragment extends Fragment {

    public EventsListFragment() {}

    List<String> headers = new ArrayList<String>();
    HashMap<String, List<Article>> events = new HashMap<String, List<Article>>();

    View v;
    ExpandableListView lv;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        v= inflater.inflate(R.layout.events_exlistview,
                container, false);
        final SwipeRefreshLayout swipeLayout=(SwipeRefreshLayout) getActivity().findViewById(R.id.swipe_container);
        final ExpandableListView exListView = (ExpandableListView) v.findViewById(R.id.events_exlistview);
        exListView.getViewTreeObserver().addOnScrollChangedListener(new ViewTreeObserver.OnScrollChangedListener() {
            @Override
            public void onScrollChanged() {
                int scrollY = exListView.getScrollY();
                if(scrollY == 0) swipeLayout.setEnabled(true);
                else swipeLayout.setEnabled(false);

            }
        });

        return v;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        ArticleDataSource datasource;

        ArticleDataSourceOptions options = new ArticleDataSourceOptions(
                ArticleSQLiteHelper.TABLE_EVENTS, getString(R.string.events_url),
                getResources().getStringArray(R.array.events_parser_names),
                ArticleParser.HtmlTags.IGNORE_HTML_TAGS, ArticleDataSource.SortOrder.GET_FUTURE,
                "40");
        datasource = new ArticleDataSource(getActivity().getApplicationContext(),options);
        datasource.open();

        List<Article> articles;
        articles = datasource.getAllArticles();
        //articleStore = (ArticleStore) bundle.getSerializable("articleStore");
        //articles = articleStore.allArticles();
        datasource.close();

        int dayOfYear = -1;
        String currentHeader = "";
        List<Article> eventsInDay = new ArrayList<Article>();


        for (Article article : articles) {
            Date date = article.date;
            if (date == null) {
                return;
            }
            Calendar cal = Calendar.getInstance();
            cal.setTime(date);
            int thisDay = cal.get(Calendar.DAY_OF_YEAR);

            if (thisDay != dayOfYear) {
                dayOfYear = thisDay;
                if (!(currentHeader.equals(""))) {
                    this.events.put(currentHeader, eventsInDay);
                    eventsInDay = new ArrayList<Article>();
                }
                SimpleDateFormat hFormat = new SimpleDateFormat("EEEE, MMMM d");
                String headerString = hFormat.format(date);
                this.headers.add(headerString);
                currentHeader = headerString;

            }
            eventsInDay.add(article);
        }
        if (eventsInDay.size() > 0) {
            this.events.put(currentHeader, eventsInDay);
        }

        this.lv = (ExpandableListView) v.findViewById(R.id.events_exlistview);

        EventsArrayAdapter adapter = new EventsArrayAdapter(getActivity(), this.headers, this.events);
        this.lv.setAdapter(adapter);

        if (getActivity().findViewById(R.id.frame_pager) == null) {
            if (articles.size() > 0) {
                sendToDetailFragment(0);
            }
        }

        ExpandableListView.OnGroupClickListener gcl = new ExpandableListView.OnGroupClickListener() {
            @Override
            public boolean onGroupClick(ExpandableListView parent, View v, int groupPosition, long id) {
                parent.expandGroup(groupPosition);
                return true;
            }
        };
        this.lv.setOnGroupClickListener(gcl);

        // Listview on child click listener
        this.lv.setOnChildClickListener(new ExpandableListView.OnChildClickListener() {

            @Override
            public boolean onChildClick(ExpandableListView parent, View v,
                                        int groupPosition, int childPosition, long id) {
                // Create new fragment and transaction
                String header = headers.get(groupPosition);
                List<Article> groupArticles = events.get(header);
                Article sendArticle = groupArticles.get(childPosition);
                if (sendArticle.details.equals("")) {
                    //do nothing
                } else {
                    sendToDetailFragment(childPosition);
                }
                return false;
            }
        });


    }

    private void sendToDetailFragment(int i) {

        EventPager newFragment = new EventPager();
        Bundle bundle = new Bundle();
        bundle.putInt("position", i);
        newFragment.setArguments(bundle);
        if (getActivity().findViewById(R.id.events_frame) != null) {
            FragmentTransaction transaction = getActivity().getSupportFragmentManager().beginTransaction();
            transaction.replace(R.id.events_frame, newFragment);
            transaction.addToBackStack(null);
            transaction.commit();
        } else {
            FragmentTransaction transaction = getActivity().getSupportFragmentManager().beginTransaction();
            transaction.replace(R.id.frame_detail_container, newFragment);
            transaction.addToBackStack(null);
            transaction.commit();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        for (int i=0; i< this.headers.size(); i++) {
            this.lv.expandGroup(i);
        }
    }

}
package st.crosscheck.fishfeeder.list;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import st.crosscheck.fishfeeder.R;
import st.crosscheck.fishfeeder.data.FeedingTime;

/**
 * A Holder for the RecyclerView list.
 *
 * @author Erik Berglund
 */
public class FeedingTimeHolder extends RecyclerView.ViewHolder
{
    private final TextView timeView;
    private final TextView feedAmountview;

    public FeedingTimeHolder(@NonNull View itemView)
    {
        super(itemView);
        timeView = itemView.findViewById(R.id.list_item_time);
        feedAmountview = itemView.findViewById(R.id.list_item_feed_amount);
    }

    public void setFeedingTime(FeedingTime ft)
    {
        timeView.setText(ft.getFormattedTime());
        feedAmountview.setText(ft.seconds+" s");
    }
}

package st.crosscheck.fishfeeder.list;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import st.crosscheck.fishfeeder.R;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import st.crosscheck.fishfeeder.Client;
import st.crosscheck.fishfeeder.data.FeedingTime;

/**
 * An adapter for the RecyclerView list.
 *
 * @author Erik Berglund
 */
public class FeedingTimeAdapter  extends RecyclerView.Adapter<FeedingTimeHolder>
{
    private final Client client;

    public FeedingTimeAdapter(Client client)
    {
        this.client = client;
    }

    @NonNull
    @Override
    public FeedingTimeHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
    {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.feedint_time_list_entry,parent,false);
        return new FeedingTimeHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FeedingTimeHolder holder, int position)
    {
        FeedingTime ft = client.getFeedingTimes().get(position);
        holder.setFeedingTime(ft);
    }

    @Override
    public int getItemCount()
    {
        synchronized (client)
        {
            return client.getFeedingTimes().size();
        }
    }

    public FeedingTime getFeedingTime(int position)
    {
        synchronized (client)
        {
            if(client.getFeedingTimes().size() > position)
            {
                return client.getFeedingTimes().get(position);
            }
            else
            {
                return null;
            }
        }
    }
}

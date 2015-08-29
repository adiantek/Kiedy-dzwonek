package ovh.adiantek.android.kiedydzwonek;

import android.app.Activity;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextSwitcher;
import android.widget.TextView;
import android.widget.ViewSwitcher;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.w3c.dom.Text;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.logging.Logger;

public class MainActivity extends Activity implements View.OnClickListener, ViewSwitcher.ViewFactory, Runnable {
    private Configuration config;
    private Handler handler;
    private View[] views = new View[35];
    private String text;
    private TextSwitcher h,m,s,lh,lm,ls;
    private TextView st,lst;
    private String texth,textm,texts,textlh,textlm,textls;
    private ProgressBar bar;
    private Gson gson() {
        return new GsonBuilder().setPrettyPrinting().create();
    }
    private GregorianCalendar getTime() {
        GregorianCalendar gc = new GregorianCalendar();
        gc.setTimeInMillis(gc.getTimeInMillis()+config.mobileDfference);
        return gc;
    }
    private int rawHour(GregorianCalendar gc) {
        int hour = gc.get(Calendar.HOUR_OF_DAY);
        hour*=60;
        hour+=gc.get(Calendar.MINUTE);
        hour*=60;
        hour+=gc.get(Calendar.SECOND);
        hour*=1000;
        hour+=gc.get(Calendar.MILLISECOND);
        return hour;
    }
    private int getNextBell(int hour) {
        for(int i=0; i<config.bells.length; i++)
            if(config.bells[i]*1000>hour)
                return (config.bells[i]*1000)-hour;
        return config.bells[0]*1000-hour+86400000;
    }
    private int getPrevBell(int hour) {
        for(int i=config.bells.length-1; i>=0; i--)
            if(config.bells[i]*1000<hour)
                return hour-(config.bells[i]*1000);
        return config.bells[0]*1000-hour+86400000;
    }
    private void loadConfiguration() {
        Configuration newConfig = null;
        Throwable error = null;
        try {
            FileInputStream stream = openFileInput("configuration.json");
            InputStreamReader reader = new InputStreamReader(stream);
            newConfig = gson().fromJson(reader, Configuration.class);
            reader.close();
        } catch(Throwable t) {
        }
        if(newConfig!=null) {
            config=newConfig;
        } else if(config==null)
            config=new Configuration();
    }
    private void saveConfiguration() {
        try {
            String str = gson().toJson(config);
            FileOutputStream stream = openFileOutput("configuration.json", 0);
            stream.write(str.getBytes(Charset.forName("UTF-8")));
            stream.close();
        } catch(Throwable t) {
            //TODO show error and synchronize with Wear
        }
    }
    private int[] setProgressBar(int hour) {
        hour/=1000;
        int nextBell = 0;
        for(int i=0; i<config.bells.length; i++) {
            if(config.bells[i]>hour) {
                nextBell=i;
                break;
            }
        }
        int total = 0, remained = 0;
        if(nextBell==0) {
            total=config.bells[0]-config.bells[config.bells.length-1]+86400;
            remained=hour-config.bells[config.bells.length-1];
            if(remained<0)
                remained+=86400;
        } else {
            total=config.bells[nextBell]-config.bells[nextBell-1];
            remained=hour-config.bells[nextBell-1];
        }
        return new int[]{remained, total};
    }
    private void loadViews() {
        try {
            Class<R.id> c = R.id.class;
            for(int i=0; i<35; i++) {
                views[i]=findViewById(c.getDeclaredField("_"+i).getInt(null));
            }
        } catch(Throwable t) {
            if(t instanceof RuntimeException)
                throw (RuntimeException) t;
            if(t instanceof Error)
                throw (Error) t;
            throw new RuntimeException("Cannot load views", t);
        }
        h=(TextSwitcher) views[8];
        m=(TextSwitcher) views[10];
        s=(TextSwitcher) views[12];
        st=(TextView) views[14];
        lh=(TextSwitcher) views[16];
        lm=(TextSwitcher) views[18];
        ls=(TextSwitcher) views[20];
        lst=(TextView) views[22];
        bar=(ProgressBar) views[32];
    }
    private void addListeners() {
        ((Button)views[ 0]).setOnClickListener(new ChangeTime(-3600000)); //-1 in hours
        ((Button)views[ 2]).setOnClickListener(new ChangeTime(-60000)); //-1 in minutes
        ((Button)views[ 4]).setOnClickListener(new ChangeTime(-1000)); //-1 in seconds
        ((Button)views[ 6]).setOnClickListener(new ChangeTime(-100)); //-1 in ms
        ((Button)views[23]).setOnClickListener(new ChangeTime(3600000)); //+1 in hours
        ((Button)views[25]).setOnClickListener(new ChangeTime(60000)); //+1 in minutes
        ((Button)views[27]).setOnClickListener(new ChangeTime(1000)); //+1 in seconds
        ((Button)views[29]).setOnClickListener(new ChangeTime(100)); //+1 in ms
        ((Button)views[30]).setOnClickListener(new ChangeTime(1)); //calibrate
        ((Button)views[31]).setOnClickListener(new ChangeTime(0)); //reset button
    }

    private void addAnimations() {
        for(int i=0; i<35; i++) {
            if(views[i] instanceof TextView) {
                ((TextView)views[i]).setTextColor(0xff000000);
            }
            if(i==34) {
                ((Button)views[i]).setText(config.showNotification?"Ukryj powiadomienie":"Pokaż powiadomienie");
                ((Button)views[i]).setOnClickListener(this);
            }
            if(views[i] instanceof TextSwitcher) {
                TextSwitcher ts = (TextSwitcher) views[i];
                ts.setFactory(this);
                Animation in, out;
                if(i>15) {
                    out = AnimationUtils.loadAnimation(this, R.anim.slide_down_out);
                    in = AnimationUtils.loadAnimation(this, R.anim.slide_down_in);
                } else {
                    out = AnimationUtils.loadAnimation(this, R.anim.slide_up_out);
                    in = AnimationUtils.loadAnimation(this, R.anim.slide_up_in);
                }
                ts.setInAnimation(in);
                ts.setOutAnimation(out);
            }
        }
    }
    private void addHandler() {
        handler=new Handler();
        handler.postDelayed(this, 0);
    }
    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.fadescale_in, R.anim.fadescale_out);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(R.anim.fadescale_in, R.anim.fadescale_out);
        setContentView(R.layout.activity_main);
        loadConfiguration();
        loadViews();
        addListeners();
        addAnimations();
        addHandler();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onClick(View v) {
        config.showNotification=((Button)v).getText().charAt(0)=='P';
        //TODO start service
        ((Button)v).setText(config.showNotification?"Ukryj powiadomienie":"Pokaż powiadomienie");
        saveConfiguration();
    }

    @Override
    public View makeView() {
        TextView tv = new TextView(this);
        tv.setTextSize(32);
        tv.setGravity(Gravity.CENTER);
        tv.setTextColor(0xff000000);
        return tv;
    }

    @Override
    public void run() {
        GregorianCalendar gc = getTime();
        text=String.format("%02d", gc.get(Calendar.HOUR_OF_DAY));
        if(!text.equals(texth)) {
            h.setText(text);
            texth=text;
        }
        text=String.format("%02d", gc.get(Calendar.MINUTE));
        if(!text.equals(textm)) {
            m.setText(text);
            textm=text;
        }
        text=String.format("%02d", gc.get(Calendar.SECOND));
        if(!text.equals(texts)) {
            s.setText(text);
            texts=text;
        }
        text=String.format("%03d", gc.get(Calendar.MILLISECOND));
        if(!text.equals(st.getText().toString())) {
            st.setText(text);
        }
        int rawHour = rawHour(gc);
        int nextBell = getNextBell(rawHour);

        text=String.format("%03d", nextBell%1000);
        if(!text.equals(lst.getText().toString())) {
            lst.setText(text);
        }
        nextBell/=1000;
        text=String.format("%02d", nextBell%60);
        if(!text.equals(textls)) {
            ls.setText(text);
            textls=text;
        }
        nextBell/=60;
        text=String.format("%02d", nextBell%60);
        if(!text.equals(textlm)) {
            lm.setText(text);
            textlm=text;
        }
        nextBell/=60;
        text=String.format("%02d", nextBell);
        if(!text.equals(textlh)) {
            lh.setText(text);
            textlh=text;
        }
        int[] bells = setProgressBar(rawHour);
        bar.setProgress(bells[0]);
        bar.setMax(bells[1]);
        handler.postDelayed(this, 1);
    }

    private class ChangeTime implements Button.OnClickListener {
        private final long toAdd;
        private ChangeTime(long toAdd) {
            this.toAdd=toAdd;
        }
        @Override
        public void onClick(View v) {
            if(toAdd==0)
                config.mobileDfference=0;
            else if(toAdd==1) {
                GregorianCalendar gc = getTime();
                int rawHour = rawHour(gc);
                int n = getNextBell(rawHour);
                int p = getPrevBell(rawHour);
                if(n<p) {
                    config.mobileDfference+=n;
                } else {
                    config.mobileDfference-=n;
                }
            } else
                config.mobileDfference+=toAdd;
            saveConfiguration();
        }
    }
}

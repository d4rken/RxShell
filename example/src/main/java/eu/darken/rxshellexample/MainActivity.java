package eu.darken.rxshellexample;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import eu.darken.rxshell.cmd.Cmd;
import eu.darken.rxshell.cmd.RxCmdShell;
import io.reactivex.android.schedulers.AndroidSchedulers;
import timber.log.Timber;

public class MainActivity extends AppCompatActivity {
    @BindView(R.id.output) TextView output;
    @BindView(R.id.input) TextView input;
    @BindView(R.id.execute) Button execute;
    private RxCmdShell.Session session;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Timber.plant(new Timber.DebugTree());
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        execute.setVisibility(View.INVISIBLE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        RxCmdShell rxCommandShell = RxCmdShell.builder().build();
        rxCommandShell.open()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(session -> {
                    execute.setVisibility(View.VISIBLE);
                    MainActivity.this.session = session;
                });
    }

    @OnClick(R.id.execute)
    public void onExecute(View v) {
        Cmd.builder(input.getText().toString()).submit(session)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    output.setText(result.getCmd().toString());
                    output.append("\n\n");

                    for (String o : result.merge()) output.append(o + "\n");

                    output.append("\n");
                    output.append(result.toString());
                });
    }

    @Override
    protected void onPause() {
        super.onPause();
        session.close()
                .doOnSubscribe(d -> session = null)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(i -> execute.setVisibility(View.INVISIBLE));
    }

    @Override
    protected void onDestroy() {
        Timber.uprootAll();
        super.onDestroy();
    }
}

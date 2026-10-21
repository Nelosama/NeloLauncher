package tv.nelo.fixture;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** A real, offline external application for repeatable launcher open/return tests. Never bundled into Nelo. */
public class FixtureActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(0xff172d43);
        TextView title = new TextView(this);
        title.setText("Aplicación de prueba · Nelo TV");
        title.setTextSize(28);
        title.setTextColor(0xffffffff);
        root.addView(title);
        Button back = new Button(this);
        back.setText("Volver a Nelo");
        back.setOnClickListener(view -> finish());
        root.addView(back);
        setContentView(root);
    }
}

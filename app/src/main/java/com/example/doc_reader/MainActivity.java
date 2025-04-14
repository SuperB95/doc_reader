package com.example.doc_reader;

import android.os.Bundle;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private TextView welcomeText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        welcomeText = findViewById(R.id.welcome_text);

        Animation fadeIn = new AlphaAnimation(0, 1);
        fadeIn.setDuration(2000);
        welcomeText.startAnimation(fadeIn);
        welcomeText.setAlpha(1);
    }
}

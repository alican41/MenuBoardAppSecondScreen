package com.example.menuboardapptwo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import com.bumptech.glide.Glide;
import com.example.menuboardapptwo.databinding.ActivityMainBinding;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private PlayerView videoView;
    private ExoPlayer exoPlayer;
    private Handler handler;
    private Runnable imageRunnable;

    private final List<String> videoList = new ArrayList<>();
    private final List<String> imageList = new ArrayList<>();
    private int videoIndex = 0;
    private int imageIndex = 0;

    private enum Mode { VIDEO, IMAGE }
    private Mode currentMode = Mode.VIDEO;

    private boolean isConnected = true;

    private final StorageReference storageRef = FirebaseStorage.getInstance().getReference().child("MenuBoard");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        videoView = binding.videoView;
        exoPlayer = new ExoPlayer.Builder(this).build();
        videoView.setPlayer(exoPlayer);

        handler = new Handler();

        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_ENDED && currentMode == Mode.VIDEO) {
                    videoIndex++;
                    if (videoIndex >= videoList.size()) {
                        videoIndex = 0;
                        currentMode = Mode.IMAGE;
                        showNextImage();
                    } else {
                        playVideo();
                    }
                }
            }
        });

        registerReceiver(networkReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        fetchMediaFiles();
    }

    private void fetchMediaFiles() {
        storageRef.listAll().addOnSuccessListener(result -> {
            for (StorageReference ref : result.getItems()) {
                ref.getDownloadUrl().addOnSuccessListener(uri -> {
                    String url = uri.toString().toLowerCase();
                    if (url.contains(".mp4")) {
                        videoList.add(uri.toString());
                        if (videoList.size() == 1 && currentMode == Mode.VIDEO) {
                            playVideo();
                        }
                    } else if (url.contains(".jpg") || url.contains(".jpeg") || url.contains(".png")) {
                        imageList.add(uri.toString());
                    }
                });
            }
        }).addOnFailureListener(e ->
                Toast.makeText(this, "Medya dosyaları alınamadı", Toast.LENGTH_LONG).show()
        );
    }

    private void playVideo() {
        if (videoList.isEmpty() || !isConnected) return;

        runOnUiThread(() -> {
            currentMode = Mode.VIDEO;
            binding.imageView.setVisibility(View.GONE);
            binding.imageViewNoConnection.setVisibility(View.GONE);
            videoView.setVisibility(View.VISIBLE);

            exoPlayer.clearMediaItems();
            MediaItem item = MediaItem.fromUri(Uri.parse(videoList.get(videoIndex)));
            exoPlayer.setMediaItem(item);
            exoPlayer.prepare();
            exoPlayer.play();
        });
    }

    private void showNextImage() {
        if (imageList.isEmpty() || !isConnected) return;

        runOnUiThread(() -> {
            currentMode = Mode.IMAGE;
            videoView.setVisibility(View.GONE);
            binding.imageViewNoConnection.setVisibility(View.GONE);
            binding.imageView.setVisibility(View.VISIBLE);

            Glide.with(this).load(imageList.get(imageIndex)).into(binding.imageView);

            imageRunnable = () -> {
                imageIndex++;
                if (imageIndex >= imageList.size()) {
                    imageIndex = 0;
                    currentMode = Mode.VIDEO;
                    playVideo();
                } else {
                    showNextImage();
                }
            };
            handler.postDelayed(imageRunnable, 6000); // 6 saniye sonra
        });
    }

    private void showNoConnectionScreen() {
        runOnUiThread(() -> {
            exoPlayer.pause();
            handler.removeCallbacks(imageRunnable);
            binding.videoView.setVisibility(View.GONE);
            binding.imageView.setVisibility(View.GONE);
            binding.imageViewNoConnection.setVisibility(View.VISIBLE);
            Glide.with(this).load(R.drawable.noconnectionscreen1).into(binding.imageViewNoConnection);
        });
    }

    private void resumePlayback() {
        runOnUiThread(() -> {
            if (currentMode == Mode.VIDEO && !videoList.isEmpty()) {
                playVideo();
            } else if (currentMode == Mode.IMAGE && !imageList.isEmpty()) {
                showNextImage();
            }
        });
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnected();
    }

    private final BroadcastReceiver networkReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean newState = isNetworkAvailable();
            if (newState != isConnected) {
                isConnected = newState;
                if (!isConnected) {
                    showNoConnectionScreen();
                } else {
                    resumePlayback();
                }
            }
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(networkReceiver);
        exoPlayer.release();
        handler.removeCallbacks(imageRunnable);
    }
}

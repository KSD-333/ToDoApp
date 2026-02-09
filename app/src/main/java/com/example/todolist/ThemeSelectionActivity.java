package com.example.todolist;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class ThemeSelectionActivity extends AppCompatActivity {

    private SharedPreferences prefs;
    private static final String PREF_BG = "app_background";
    private static final int PERMISSION_REQUEST_CODE = 200;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_theme_selection);

        prefs = getSharedPreferences("settings", MODE_PRIVATE);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        TabLayout tabLayout = findViewById(R.id.tab_layout);
        ViewPager2 viewPager = findViewById(R.id.view_pager);

        viewPager.setAdapter(new ThemePagerAdapter(this));

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0:
                    tab.setText("Presets");
                    break;
                case 1:
                    tab.setText("Colors");
                    break;
                case 2:
                    tab.setText("Gallery");
                    break;
            }
        }).attach();
    }

    // ================= Pager Adapter =================
    private class ThemePagerAdapter extends FragmentStateAdapter {
        public ThemePagerAdapter(AppCompatActivity activity) {
            super(activity);
        }

        @NonNull
        @Override
        public androidx.fragment.app.Fragment createFragment(int position) {
            return ThemeListFragment.newInstance(position);
        }

        @Override
        public int getItemCount() {
            return 3;
        }
    }

    // ================= Theme List Fragment =================
    public static class ThemeListFragment extends androidx.fragment.app.Fragment {
        private int type;
        private RecyclerView recyclerView;
        private ThemeAdapter adapter;
        private List<ThemeOption> options = new ArrayList<>();
        private SharedPreferences prefs;
        private ActivityResultLauncher<String> galleryLauncher;
        private ActivityResultLauncher<String> permissionLauncher;

        public static ThemeListFragment newInstance(int type) {
            ThemeListFragment f = new ThemeListFragment();
            Bundle args = new Bundle();
            args.putInt("type", type);
            f.setArguments(args);
            return f;
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            if (getArguments() != null) {
                type = getArguments().getInt("type");
            }
            prefs = requireContext().getSharedPreferences("settings", MODE_PRIVATE);

            galleryLauncher = registerForActivityResult(
                    new ActivityResultContracts.GetContent(),
                    uri -> {
                        if (uri != null) {
                            saveTheme("uri:" + uri.toString());
                        }
                    });

            permissionLauncher = registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    isGranted -> {
                        if (isGranted) {
                            openGallery();
                        } else {
                            Toast.makeText(getContext(), "Permission required to access gallery", Toast.LENGTH_SHORT)
                                    .show();
                        }
                    });
        }

        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            recyclerView = new RecyclerView(requireContext());
            recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));
            int padding = (int) (8 * getResources().getDisplayMetrics().density);
            recyclerView.setPadding(padding, padding, padding, padding);
            return recyclerView;
        }

        @Override
        public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            loadOptions();
            adapter = new ThemeAdapter(options, this::onThemeSelected);
            recyclerView.setAdapter(adapter);
        }

        private void loadOptions() {
            options.clear();
            if (type == 0) { // Presets (Gradients)
                // Add 20 Industry Level Gradients
                options.add(new ThemeOption("gradient:#FF9A9E,#FECFEF", "Soft Love"));
                options.add(new ThemeOption("gradient:#a18cd1,#fbc2eb", "Lavender Dream"));
                options.add(new ThemeOption("gradient:#fbc2eb,#a6c1ee", "Sky High"));
                options.add(new ThemeOption("gradient:#84fab0,#8fd3f4", "Minty Fresh"));
                options.add(new ThemeOption("gradient:#e0c3fc,#8ec5fc", "Winter Neva"));
                options.add(new ThemeOption("gradient:#43e97b,#38f9d7", "Spring Greens"));
                options.add(new ThemeOption("gradient:#fa709a,#fee140", "Sunset Glow"));
                options.add(new ThemeOption("gradient:#30cfd0,#330867", "Deep Ocean"));
                options.add(new ThemeOption("gradient:#667eea,#764ba2", "Royal Blue"));
                options.add(new ThemeOption("gradient:#c471f5,#fa71cd", "Neon Life"));
                options.add(new ThemeOption("gradient:#48c6ef,#6f86d6", "Cool Blue"));
                options.add(new ThemeOption("gradient:#f78ca0,#fe9a8b", "Peach Perfect"));
                options.add(new ThemeOption("gradient:#ffffff,#e6e9f0", "Clean White"));
                options.add(new ThemeOption("gradient:#fdfbfb,#ebedee", "Snowy Ash"));
                options.add(new ThemeOption("gradient:#29323c,#485563", "Dark Metal"));
                options.add(new ThemeOption("gradient:#1e3c72,#2a5298", "Midnight"));
                options.add(new ThemeOption("gradient:#37ecba,#72afd3", "Aqua Splash"));
                options.add(new ThemeOption("gradient:#d299c2,#fef9d7", "Pastel Dreams"));
                options.add(new ThemeOption("gradient:#f5f7fa,#c3cfe2", "Cloudy Knopf"));
                options.add(new ThemeOption("gradient:#13547a,#80d0c7", "Teal Love"));
            } else if (type == 1) { // Colors
                // Add 15 Colors
                options.add(new ThemeOption("color:#FFFFFF", "Clean White"));
                options.add(new ThemeOption("color:#F5F5F5", "Light Gray"));
                options.add(new ThemeOption("color:#FFEBEE", "Soft Red"));
                options.add(new ThemeOption("color:#F3E5F5", "Soft Purple"));
                options.add(new ThemeOption("color:#E3F2FD", "Soft Blue"));
                options.add(new ThemeOption("color:#E8F5E9", "Soft Green"));
                options.add(new ThemeOption("color:#FFF3E0", "Soft Orange"));
                options.add(new ThemeOption("color:#E0F7FA", "Cyan Mist"));
                options.add(new ThemeOption("color:#FFFDE7", "Pale Yellow"));
                options.add(new ThemeOption("color:#ECEFF1", "Blue Gray"));
                options.add(new ThemeOption("color:#212121", "Pure Black"));
                options.add(new ThemeOption("color:#37474F", "Dark Slate"));
                options.add(new ThemeOption("color:#1A237E", "Navy"));
                options.add(new ThemeOption("color:#1B5E20", "Dark Green"));
                options.add(new ThemeOption("color:#263238", "Midnight"));
            } else { // Gallery
                options.add(new ThemeOption("CUSTOM_ADD", "Select from Gallery"));
                options.add(new ThemeOption("color:#00000000", "Reset to Default"));
            }
        }

        private void onThemeSelected(ThemeOption option) {
            if ("CUSTOM_ADD".equals(option.value)) {
                checkPermissionAndOpenGallery();
            } else if (option.value.startsWith("color:#00000000")) {
                saveTheme("");
            } else {
                saveTheme(option.value);
            }
        }

        private void saveTheme(String value) {
            prefs.edit().putString(PREF_BG, value).apply();
            Toast.makeText(getContext(), "Theme Applied!", Toast.LENGTH_SHORT).show();
            // Notify current adapters to update selection checkmark
            if (adapter != null)
                adapter.notifyDataSetChanged();

            // Require Main Activity to refresh if possible, or just finishing will reload
            // on resume
        }

        private void checkPermissionAndOpenGallery() {
            String permission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    ? Manifest.permission.READ_MEDIA_IMAGES
                    : Manifest.permission.READ_EXTERNAL_STORAGE;

            if (ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED) {
                openGallery();
            } else {
                permissionLauncher.launch(permission);
            }
        }

        private void openGallery() {
            galleryLauncher.launch("image/*");
        }
    }

    // ================= Models & Adapter =================
    private static class ThemeOption {
        String value; // "color:...", "gradient:...", "uri:..."
        String name;

        ThemeOption(String value, String name) {
            this.value = value;
            this.name = name;
        }
    }

    private static class ThemeAdapter extends RecyclerView.Adapter<ThemeAdapter.VH> {
        List<ThemeOption> list;
        OnThemeSelectListener listener;

        interface OnThemeSelectListener {
            void onSelect(ThemeOption option);
        }

        ThemeAdapter(List<ThemeOption> list, OnThemeSelectListener listener) {
            this.list = list;
            this.listener = listener;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_theme_option, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            ThemeOption option = list.get(position);
            holder.bind(option);
            holder.itemView.setOnClickListener(v -> listener.onSelect(option));
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        class VH extends RecyclerView.ViewHolder {
            ImageView ivBg, ivCheck;
            TextView tvName;

            VH(View v) {
                super(v);
                ivBg = v.findViewById(R.id.iv_bg_preview);
                ivCheck = v.findViewById(R.id.iv_selected_checkmark);
                tvName = v.findViewById(R.id.tv_theme_name);
            }

            void bind(ThemeOption option) {
                tvName.setText(option.name);

                // Show checkmark if selected
                SharedPreferences prefs = itemView.getContext().getSharedPreferences("settings", MODE_PRIVATE);
                String current = prefs.getString(PREF_BG, "");
                if (current.equals(option.value)) {
                    ivCheck.setVisibility(View.VISIBLE);
                } else {
                    ivCheck.setVisibility(View.GONE);
                }

                if (option.value.equals("CUSTOM_ADD")) {
                    ivBg.setImageDrawable(new ColorDrawable(Color.LTGRAY));
                    ivBg.setScaleType(ImageView.ScaleType.CENTER);
                    ivBg.setImageResource(R.drawable.ic_add_blue); // Fallback or resource
                } else if (option.value.startsWith("color:")) {
                    try {
                        int color = Color.parseColor(option.value.substring(6));
                        ivBg.setImageDrawable(new ColorDrawable(color));
                    } catch (Exception e) {
                        ivBg.setImageDrawable(new ColorDrawable(Color.GRAY));
                    }
                } else if (option.value.startsWith("gradient:")) {
                    try {
                        String[] colors = option.value.substring(9).split(",");
                        int[] colorInts = new int[colors.length];
                        for (int i = 0; i < colors.length; i++)
                            colorInts[i] = Color.parseColor(colors[i]);
                        GradientDrawable gd = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colorInts);
                        ivBg.setImageDrawable(gd);
                    } catch (Exception e) {
                        ivBg.setImageDrawable(new ColorDrawable(Color.MAGENTA));
                    }
                } else if (option.value.startsWith("uri:")) {
                    // Load image? For security permissions URI access might be tricky across
                    // restarts
                    // For now just show placeholder or try validation
                    ivBg.setImageDrawable(new ColorDrawable(Color.DKGRAY));
                }
            }
        }
    }
}

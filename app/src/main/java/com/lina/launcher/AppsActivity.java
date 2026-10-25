package com.lina.launcher;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AppsActivity extends Activity {

    private static class App {
        String label;
        Drawable icon;
        String pkg;
        String cls;
    }

    private final List<App> apps = new ArrayList<>();

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadApps();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFFFFF6F9, 0xFFFDE4EE, 0xFFF8C9DA});
        root.setBackground(bg);
        root.setPadding(dp(20), dp(14), dp(20), dp(10));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView back = new TextView(this);
        back.setText("‹  Volver");
        back.setTextColor(0xFF8A2450);
        back.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        back.setPadding(dp(8), dp(10), dp(24), dp(10));
        back.setOnClickListener(v -> finish());
        header.addView(back);

        TextView title = new TextView(this);
        title.setText("Mis aplicaciones");
        title.setTextColor(0xFF5E1636);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        header.addView(title);
        root.addView(header);

        GridView grid = new GridView(this);
        grid.setNumColumns(GridView.AUTO_FIT);
        grid.setColumnWidth(dp(110));
        grid.setVerticalSpacing(dp(12));
        grid.setHorizontalSpacing(dp(8));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setSelector(new GradientDrawable());
        grid.setAdapter(new Adapter());
        grid.setOnItemClickListener((parent, view, position, id) -> {
            App a = apps.get(position);
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_LAUNCHER);
            i.setClassName(a.pkg, a.cls);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(i);
            } catch (ActivityNotFoundException | SecurityException ignored) { }
        });
        root.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> list = pm.queryIntentActivities(main, 0);
        for (ResolveInfo ri : list) {
            if (ri.activityInfo.packageName.equals(getPackageName())) continue;
            App a = new App();
            a.label = String.valueOf(ri.loadLabel(pm));
            a.icon = ri.loadIcon(pm);
            a.pkg = ri.activityInfo.packageName;
            a.cls = ri.activityInfo.name;
            apps.add(a);
        }
        final Collator c = Collator.getInstance();
        Collections.sort(apps, (x, y) -> c.compare(x.label, y.label));
    }

    private class Adapter extends BaseAdapter {
        @Override
        public int getCount() { return apps.size(); }

        @Override
        public Object getItem(int position) { return apps.get(position); }

        @Override
        public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout cell;
            ImageView icon;
            TextView label;
            if (convertView == null) {
                cell = new LinearLayout(AppsActivity.this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                cell.setPadding(dp(6), dp(10), dp(6), dp(10));
                GradientDrawable card = new GradientDrawable();
                card.setColor(0x9EFFFFFF);
                card.setCornerRadius(dp(18));
                card.setStroke(dp(1), 0xFFE8AAC4);
                cell.setBackground(card);

                icon = new ImageView(AppsActivity.this);
                icon.setId(View.generateViewId());
                cell.addView(icon, new LinearLayout.LayoutParams(dp(52), dp(52)));

                label = new TextView(AppsActivity.this);
                label.setTextColor(0xFF5E1636);
                label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                label.setGravity(Gravity.CENTER);
                label.setMaxLines(2);
                label.setEllipsize(TextUtils.TruncateAt.END);
                label.setPadding(0, dp(6), 0, 0);
                cell.addView(label);
                cell.setTag(new View[]{icon, label});
            } else {
                cell = (LinearLayout) convertView;
            }
            View[] views = (View[]) cell.getTag();
            App a = apps.get(position);
            ((ImageView) views[0]).setImageDrawable(a.icon);
            ((TextView) views[1]).setText(a.label);
            return cell;
        }
    }
}

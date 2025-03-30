package app.olauncher.ultra;

import static android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.AlarmClock;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity implements View.OnClickListener, View.OnLongClickListener {
    private final int FLAG_LAUNCH_APP = 0;
    private final List<AppModel> appList = new ArrayList<>();
    private Set<String> notifiedPackages = new HashSet<>();
    private Drawable badgeDrawable;

    private Prefs prefs;
    private View appDrawer;
    private EditText search;
    private ListView appListView;
    private AppAdapter appAdapter;
    private LinearLayout homeAppsLayout;
    private TextView homeApp1, homeApp2, homeApp3, homeApp4, homeApp5, homeApp6, setDefaultLauncher;
    private LocalBroadcastManager localBroadcastManager;
    private BroadcastReceiver notificationReceiver;


    public interface AppClickListener {
        void appClicked(AppModel appModel, int flag);

        void appLongPress(AppModel appModel);
    }

    @Override
    public void onBackPressed() {
        if (appDrawer.getVisibility() == View.VISIBLE) backToHome();
        else super.onBackPressed();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Window window = getWindow();
        window.addFlags(FLAG_LAYOUT_NO_LIMITS);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        badgeDrawable = ContextCompat.getDrawable(this, R.drawable.notification_badge);

        findViewById(R.id.layout_main).setOnTouchListener(getSwipeGestureListener(this));
        initClickListeners();

        prefs = new Prefs(this);
        search = findViewById(R.id.search);
        homeAppsLayout = findViewById(R.id.home_apps_layout);
        appDrawer = findViewById(R.id.app_drawer_layout);

        appAdapter = new AppAdapter(this, appList, getAppClickListener());
        appListView = findViewById(R.id.app_list_view);
        appListView.setAdapter(appAdapter);

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}
            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                appAdapter.getFilter().filter(charSequence);
            }
            @Override
            public void afterTextChanged(Editable editable) {}
        });
        appListView.setOnScrollListener(getScrollListener());

        localBroadcastManager = LocalBroadcastManager.getInstance(this);
        setupNotificationReceiver();
        checkNotificationAccess();
    }

    @Override
    protected void onResume() {
        super.onResume();
        localBroadcastManager.registerReceiver(notificationReceiver, new IntentFilter(NotificationListener.ACTION_NOTIFICATION_UPDATE));
        notifiedPackages = NotificationListener.getNotifiedPackages();
        backToHome();
    }

    @Override
    protected void onPause() {
        super.onPause();
        localBroadcastManager.unregisterReceiver(notificationReceiver);
    }


    private void setupNotificationReceiver() {
        notificationReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (NotificationListener.ACTION_NOTIFICATION_UPDATE.equals(intent.getAction())) {
                    Set<String> updatedPackages = (Set<String>) intent.getSerializableExtra(NotificationListener.EXTRA_PACKAGES_WITH_NOTIFICATIONS);
                    if (updatedPackages != null) {
                        notifiedPackages = updatedPackages;
                        updateHomeAppBadges();
                        updateAppListBadges();
                    }
                }
            }
        };
    }

    private void checkNotificationAccess() {
        ComponentName cn = new ComponentName(this, NotificationListener.class);
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        final boolean enabled = flat != null && flat.contains(cn.flattenToString());

        if (!enabled) {
            Toast.makeText(this, "Please grant Notification Access for badges", Toast.LENGTH_LONG).show();
            try {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            } catch (Exception e) {
                Toast.makeText(this, "Could not open Notification Access settings", Toast.LENGTH_SHORT).show();
            }
        } else {
            toggleNotificationListenerService();
        }
    }

    private void toggleNotificationListenerService() {
        PackageManager pm = getPackageManager();
        ComponentName componentName = new ComponentName(this, NotificationListener.class);
        pm.setComponentEnabledSetting(componentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        pm.setComponentEnabledSetting(componentName,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
    }


    @Override
    public void onClick(View view) {
        int viewId = view.getId();
        if (viewId == R.id.set_as_default_launcher) {
            resetDefaultLauncher();
        } else if (viewId == R.id.clock) {
            try {
                startActivity(new Intent(AlarmClock.ACTION_SHOW_ALARMS));
            } catch (Exception e) {
                Toast.makeText(this, "Clock app not found", Toast.LENGTH_SHORT).show();
            }
        } else if (viewId == R.id.date) {
            try {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.addCategory(Intent.CATEGORY_APP_CALENDAR);
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "Calendar app not found", Toast.LENGTH_SHORT).show();
            }
        } else {
            try {
                int location = Integer.parseInt(view.getTag().toString());
                homeAppClicked(location);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public boolean onLongClick(View view) {
        try {
            int location = Integer.parseInt(view.getTag().toString());
            showAppList(location);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }

    private void initClickListeners() {
        setDefaultLauncher = findViewById(R.id.set_as_default_launcher);
        setDefaultLauncher.setOnClickListener(this);

        findViewById(R.id.clock).setOnClickListener(this);
        findViewById(R.id.date).setOnClickListener(this);

        homeApp1 = findViewById(R.id.home_app_1);
        homeApp2 = findViewById(R.id.home_app_2);
        homeApp3 = findViewById(R.id.home_app_3);
        homeApp4 = findViewById(R.id.home_app_4);
        homeApp5 = findViewById(R.id.home_app_5);
        homeApp6 = findViewById(R.id.home_app_6);

        homeApp1.setOnClickListener(this);
        homeApp2.setOnClickListener(this);
        homeApp3.setOnClickListener(this);
        homeApp4.setOnClickListener(this);
        homeApp5.setOnClickListener(this);
        homeApp6.setOnClickListener(this);

        homeApp1.setOnLongClickListener(this);
        homeApp2.setOnLongClickListener(this);
        homeApp3.setOnLongClickListener(this);
        homeApp4.setOnLongClickListener(this);
        homeApp5.setOnLongClickListener(this);
        homeApp6.setOnLongClickListener(this);
    }

    private void populateHomeApps() {
        homeApp1.setText(prefs.getAppName(1));
        homeApp2.setText(prefs.getAppName(2));
        homeApp3.setText(prefs.getAppName(3));
        homeApp4.setText(prefs.getAppName(4));
        homeApp5.setText(prefs.getAppName(5));
        homeApp6.setText(prefs.getAppName(6));
        updateHomeAppBadges();
    }

    private void updateHomeAppBadges() {
        updateBadgeForHomeApp(homeApp1, 1);
        updateBadgeForHomeApp(homeApp2, 2);
        updateBadgeForHomeApp(homeApp3, 3);
        updateBadgeForHomeApp(homeApp4, 4);
        updateBadgeForHomeApp(homeApp5, 5);
        updateBadgeForHomeApp(homeApp6, 6);
    }

    private void updateBadgeForHomeApp(TextView textView, int location) {
        String pkg = prefs.getAppPackage(location);
        boolean hasNotification = !pkg.isEmpty() && notifiedPackages.contains(pkg);
        textView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                null,
                null,
                hasNotification ? badgeDrawable : null,
                null
        );
        textView.setCompoundDrawablePadding(hasNotification ? 8 : 0);
    }


    private void showLongPressToast() {
        Toast.makeText(this, "Long press to select app", Toast.LENGTH_SHORT).show();
    }

    private void backToHome() {
        appDrawer.setVisibility(View.GONE);
        homeAppsLayout.setVisibility(View.VISIBLE);
        appAdapter.setFlag(FLAG_LAUNCH_APP);
        hideKeyboard();
        appListView.setSelectionAfterHeaderView();
        checkForDefaultLauncher();
        populateHomeApps();
        refreshAppsList();
    }

    private void refreshAppsList() {
        new Thread(() -> {
            List<AppModel> apps = new ArrayList<>();
            try {
                UserManager userManager = (UserManager) getSystemService(Context.USER_SERVICE);
                LauncherApps launcherApps = (LauncherApps) getSystemService(Context.LAUNCHER_APPS_SERVICE);
                Set<String> currentNotifiedPackages = new HashSet<>(notifiedPackages);

                for (UserHandle profile : userManager.getUserProfiles()) {
                    for (LauncherActivityInfo activityInfo : launcherApps.getActivityList(null, profile)) {
                        String packageName = activityInfo.getApplicationInfo().packageName;
                        if (!packageName.equals(BuildConfig.APPLICATION_ID)) {
                            boolean hasNotif = currentNotifiedPackages.contains(packageName);
                            apps.add(new AppModel(
                                    activityInfo.getLabel().toString(),
                                    packageName,
                                    profile,
                                    hasNotif));
                        }
                    }
                }
                Collections.sort(apps, (app1, app2) -> app1.appLabel.compareToIgnoreCase(app2.appLabel));

                runOnUiThread(() -> {
                    synchronized (appList) {
                        appList.clear();
                        appList.addAll(apps);
                    }
                    appAdapter.notifyDataSetChanged();
                });

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void updateAppListBadges() {
        boolean changed = false;
        synchronized (appList) {
            for (AppModel app : appList) {
                boolean newHasNotification = notifiedPackages.contains(app.appPackage);
                if (app.hasNotification != newHasNotification) {
                    app.hasNotification = newHasNotification;
                    changed = true;
                }
            }
        }
        if (changed) {
            runOnUiThread(() -> appAdapter.notifyDataSetChanged());
        }
    }


    private void showAppList(int flag) {
        setDefaultLauncher.setVisibility(View.GONE);
        showKeyboard();
        search.setText("");
        appAdapter.setFlag(flag);
        homeAppsLayout.setVisibility(View.GONE);
        appDrawer.setVisibility(View.VISIBLE);
    }

    private void showKeyboard() {
        search.requestFocus();
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
    }

    private void hideKeyboard() {
        search.clearFocus();
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(search.getWindowToken(), 0);
    }

    @SuppressLint({"WrongConstant", "PrivateApi"})
    private void expandNotificationDrawer() {
        try {
            Object statusBarService = getSystemService("statusbar");
            Class<?> statusBarManager = Class.forName("android.app.StatusBarManager");
            Method method = statusBarManager.getMethod("expandNotificationsPanel");
            method.invoke(statusBarService);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void prepareToLaunchApp(AppModel appModel) {
        hideKeyboard();
        launchApp(appModel);
        backToHome();
        search.setText("");
    }

    private void homeAppClicked(int location) {
        String pkg = prefs.getAppPackage(location);
        if (pkg.isEmpty()) showLongPressToast();
        else launchApp(getAppModel(
                prefs.getAppName(location),
                pkg,
                prefs.getAppUserHandle(location)));
    }

    private void launchApp(AppModel appModel) {
        LauncherApps launcher = (LauncherApps) getSystemService(Context.LAUNCHER_APPS_SERVICE);
        List<LauncherActivityInfo> appLaunchActivityList = launcher.getActivityList(appModel.appPackage, appModel.userHandle);
        ComponentName componentName;

        if (appLaunchActivityList == null || appLaunchActivityList.isEmpty()) {
            Toast.makeText(this, "App not found", Toast.LENGTH_SHORT).show();
            return;
        }

        componentName = appLaunchActivityList.get(0).getComponentName();

        try {
            launcher.startMainActivity(componentName, appModel.userHandle, null, null);
        } catch (SecurityException securityException) {
            try {
                launcher.startMainActivity(componentName, android.os.Process.myUserHandle(), null, null);
            } catch (Exception e) {
                Toast.makeText(this, "Unable to launch app", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Unable to launch app", Toast.LENGTH_SHORT).show();
        }
    }


    private void openAppInfo(AppModel appModel) {
        LauncherApps launcher = (LauncherApps) getSystemService(Context.LAUNCHER_APPS_SERVICE);
        List<LauncherActivityInfo> activities = launcher.getActivityList(appModel.appPackage, appModel.userHandle);
        if (activities == null || activities.isEmpty()) {
            Toast.makeText(this, "App info not available", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            launcher.startAppDetailsActivity(activities.get(0).getComponentName(), appModel.userHandle, null, null);
        } catch (Exception e) {
            Toast.makeText(this, "Unable to open app info", Toast.LENGTH_SHORT).show();
        }
    }

    private void setHomeApp(AppModel appModel, int flag) {
        prefs.setHomeApp(appModel, flag);
        backToHome();
    }

    private void checkForDefaultLauncher() {
        if (BuildConfig.APPLICATION_ID.equals(getDefaultLauncherPackage()))
            setDefaultLauncher.setVisibility(View.GONE);
        else setDefaultLauncher.setVisibility(View.VISIBLE);
    }

    private String getDefaultLauncherPackage() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        ResolveInfo result = getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        if (result == null || result.activityInfo == null)
            return "";
        return result.activityInfo.packageName;
    }

    private void resetDefaultLauncher() {
        try {
            PackageManager packageManager = getPackageManager();
            ComponentName componentName = new ComponentName(this, FakeHomeActivity.class);
            packageManager.setComponentEnabledSetting(
                    componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
            );
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            startActivity(intent);
            packageManager.setComponentEnabledSetting(
                    componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
            );
        } catch (Exception e) {
            e.printStackTrace();
            openLauncherPhoneSettings();
        }

        new android.os.Handler().postDelayed(() -> {
            if (!BuildConfig.APPLICATION_ID.equals(getDefaultLauncherPackage())) {
                openLauncherPhoneSettings();
            } else {
                setDefaultLauncher.setVisibility(View.GONE);
            }
        }, 500);
    }

    private void openLauncherPhoneSettings() {
        Toast.makeText(this, "Set Ultra as default launcher", Toast.LENGTH_LONG).show();
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            intent = new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS);
        } else {
            intent = new Intent(Settings.ACTION_HOME_SETTINGS);
        }
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Could not open default app settings", Toast.LENGTH_SHORT).show();
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (Exception e2) {
                Toast.makeText(this, "Could not open settings", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void openEditSettingsPermission() {
        Toast.makeText(this, "Please grant this permission", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
        intent.setData(Uri.parse("package:" + BuildConfig.APPLICATION_ID));
        try {
            startActivity(intent);
        } catch(Exception e) {
            Toast.makeText(this, "Could not open write settings permission screen", Toast.LENGTH_SHORT).show();
        }
    }


    private AppClickListener getAppClickListener() {
        return new AppClickListener() {
            @Override
            public void appClicked(AppModel appModel, int flag) {
                if (flag == FLAG_LAUNCH_APP) prepareToLaunchApp(appModel);
                else setHomeApp(appModel, flag);
            }

            @Override
            public void appLongPress(AppModel appModel) {
                hideKeyboard();
                openAppInfo(appModel);
            }
        };
    }

    private AppModel getAppModel(String appLabel, String appPackage, String appUserHandle) {
        return new AppModel(appLabel, appPackage, getUserHandleFromString(appUserHandle), false);
    }


    private UserHandle getUserHandleFromString(String appUserHandleString) {
        UserManager userManager = (UserManager) getSystemService(Context.USER_SERVICE);
        if (appUserHandleString != null && !appUserHandleString.isEmpty()) {
            for (UserHandle userHandle : userManager.getUserProfiles()) {
                if (userHandle.toString().equals(appUserHandleString)) {
                    return userHandle;
                }
            }
        }
        return android.os.Process.myUserHandle();
    }


    private AbsListView.OnScrollListener getScrollListener() {
        return new AbsListView.OnScrollListener() {

            boolean wasOnTopWhenScrollStarted = false;

            @Override
            public void onScrollStateChanged(AbsListView listView, int scrollState) {
                if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) {
                    wasOnTopWhenScrollStarted = !listView.canScrollVertically(-1);
                    hideKeyboard();
                } else if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE) {
                    boolean currentlyAtTop = !listView.canScrollVertically(-1);

                    if (currentlyAtTop && wasOnTopWhenScrollStarted) {
                        backToHome();
                    } else if (currentlyAtTop && !wasOnTopWhenScrollStarted) {
                        showKeyboard();
                    }
                    wasOnTopWhenScrollStarted = false;
                }
            }

            @Override
            public void onScroll(AbsListView absListView, int i, int i1, int i2) {}
        };
    }

    private View.OnTouchListener getSwipeGestureListener(Context context) {
        return new OnSwipeTouchListener(context) {
            @Override
            public void onSwipeLeft() {
                super.onSwipeLeft();
                try {
                    Intent intent = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(context, "Camera app not found", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onSwipeRight() {
                super.onSwipeRight();
                try {
                    Intent intent = new Intent(Intent.ACTION_DIAL);
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(context, "Dialer app not found", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onSwipeUp() {
                super.onSwipeUp();
                showAppList(FLAG_LAUNCH_APP);
            }

            @Override
            public void onSwipeDown() {
                super.onSwipeDown();
                expandNotificationDrawer();
            }

            @Override
            public void onLongClick() {}
            @Override
            public void onDoubleClick() {}
            @Override
            public void onTripleClick() {}
            @Override
            public void onClick() {}
        };
    }

    // --- Inner Classes ---

    // AppModel remains static
    static class AppModel {
        String appLabel;
        String appPackage;
        UserHandle userHandle;
        boolean hasNotification;

        public AppModel(String appLabel, String appPackage, UserHandle userHandle, boolean hasNotification) {
            this.appLabel = appLabel;
            this.appPackage = appPackage;
            this.userHandle = userHandle;
            this.hasNotification = hasNotification;
        }
    }

    // AppAdapter is non-static
    class AppAdapter extends BaseAdapter implements Filterable {

        private final Context context;
        private final AppClickListener appClickListener;
        private List<AppModel> filteredAppsList;
        private int flag = 0;

        // ViewHolder is now also non-static
        private class ViewHolder {
            TextView appName;
            View indicator;
            View notificationBadge;
        }

        public AppAdapter(Context context, List<AppModel> apps, AppClickListener appClickListener) {
            this.context = context;
            this.appClickListener = appClickListener;
            this.filteredAppsList = new ArrayList<>(apps);
        }

        public void setFlag(int flag) {
            this.flag = flag;
        }

        @Override
        public int getCount() {
            return filteredAppsList.size();
        }

        @Override
        public Object getItem(int position) {
            return filteredAppsList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            AppModel appModel = (AppModel) getItem(position);
            ViewHolder viewHolder;
            if (convertView == null) {
                viewHolder = new ViewHolder();
                LayoutInflater inflater = LayoutInflater.from(context);
                convertView = inflater.inflate(R.layout.adapter_app, parent, false);
                viewHolder.appName = convertView.findViewById(R.id.app_name);
                viewHolder.indicator = convertView.findViewById(R.id.other_profile_indicator);
                viewHolder.notificationBadge = convertView.findViewById(R.id.notification_badge);
                convertView.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }

            viewHolder.appName.setTag(appModel);
            viewHolder.appName.setText(appModel.appLabel);
            viewHolder.appName.setOnClickListener(view -> {
                AppModel clickedAppModel = (AppModel) viewHolder.appName.getTag();
                appClickListener.appClicked(clickedAppModel, flag);
            });
            viewHolder.appName.setOnLongClickListener(view -> {
                AppModel clickedAppModel = (AppModel) viewHolder.appName.getTag();
                appClickListener.appLongPress(clickedAppModel);
                return true;
            });

            if (appModel.userHandle == android.os.Process.myUserHandle())
                viewHolder.indicator.setVisibility(View.GONE);
            else viewHolder.indicator.setVisibility(View.VISIBLE);

            viewHolder.notificationBadge.setVisibility(appModel.hasNotification ? View.VISIBLE : View.GONE);

            if (flag == FLAG_LAUNCH_APP && getCount() == 1 && search.getText().length() > 0) {
                appClickListener.appClicked(appModel, flag);
            }

            return convertView;
        }

        @Override
        public Filter getFilter() {

            return new Filter() {

                @SuppressWarnings("unchecked")
                @Override
                protected void publishResults(CharSequence constraint, FilterResults results) {
                    filteredAppsList = (List<AppModel>) results.values;
                    AppAdapter.super.notifyDataSetChanged();
                }

                @Override
                protected FilterResults performFiltering(CharSequence constraint) {
                    FilterResults results = new FilterResults();
                    List<AppModel> filteredApps = new ArrayList<>();
                    String filterPattern = constraint.toString().toLowerCase().trim();

                    synchronized (appList) {
                        if (filterPattern.isEmpty()) {
                            filteredApps.addAll(appList);
                        } else {
                            for (AppModel app : appList) {
                                if (app.appLabel.toLowerCase().contains(filterPattern)) {
                                    filteredApps.add(app);
                                }
                            }
                        }
                    }

                    results.count = filteredApps.size();
                    results.values = filteredApps;
                    return results;
                }
            };
        }

        @Override
        public void notifyDataSetChanged() {
            getFilter().filter(search.getText());
        }
    }
}
package cn.fanzha.classroom;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;

/** Shared paper-surface dialogs used by updates, story navigation, and guidance. */
public final class WashiDialog {
    public interface SelectionListener {
        void onSelected(int index);
    }

    public interface AsyncAction {
        void run(ActionHandle handle);
    }

    public static final class Action {
        final String label;
        final boolean primary;
        final boolean danger;
        final Runnable callback;
        final AsyncAction asyncCallback;

        private Action(String label, boolean primary, boolean danger, @Nullable Runnable callback,
                       @Nullable AsyncAction asyncCallback) {
            this.label = label;
            this.primary = primary;
            this.danger = danger;
            this.callback = callback;
            this.asyncCallback = asyncCallback;
        }

        public static Action primary(String label, @Nullable Runnable callback) {
            return new Action(label, true, false, callback, null);
        }

        public static Action secondary(String label, @Nullable Runnable callback) {
            return new Action(label, false, false, callback, null);
        }

        public static Action danger(String label, @Nullable Runnable callback) {
            return new Action(label, false, true, callback, null);
        }

        public static Action asyncPrimary(String label, AsyncAction callback) {
            return new Action(label, true, false, null, callback);
        }

        public static Action asyncSecondary(String label, AsyncAction callback) {
            return new Action(label, false, false, null, callback);
        }
    }

    public static final class ActionHandle {
        private final Dialog dialog;
        private final LinearLayout actionContainer;
        private final MaterialButton button;
        private final CircularProgressIndicator indicator;
        private final String originalLabel;
        private final boolean dialogCancelable;

        private ActionHandle(Dialog dialog, LinearLayout actionContainer, MaterialButton button,
                             CircularProgressIndicator indicator, String originalLabel,
                             boolean dialogCancelable) {
            this.dialog = dialog;
            this.actionContainer = actionContainer;
            this.button = button;
            this.indicator = indicator;
            this.originalLabel = originalLabel;
            this.dialogCancelable = dialogCancelable;
        }

        private void start() {
            setButtonsEnabled(actionContainer, false);
            dialog.setCancelable(false);
            dialog.setCanceledOnTouchOutside(false);
            button.setText("正在检查…");
            button.setContentDescription("正在检查更新");
            indicator.setVisibility(View.VISIBLE);
        }

        public void finish() {
            button.post(() -> {
                indicator.setVisibility(View.GONE);
                button.setText(originalLabel);
                button.setContentDescription(originalLabel);
                setButtonsEnabled(actionContainer, true);
                dialog.setCancelable(dialogCancelable);
                dialog.setCanceledOnTouchOutside(dialogCancelable);
            });
        }

        public void dismiss() {
            button.post(() -> {
                finish();
                if (dialog.isShowing()) dialog.dismiss();
            });
        }
    }

    public static final class ProgressHandle {
        private final Dialog dialog;
        private final TextView message;

        private ProgressHandle(Dialog dialog, TextView message) {
            this.dialog = dialog;
            this.message = message;
        }

        public void setMessage(String value) {
            message.setText(value);
        }

        public void dismiss() {
            if (dialog.isShowing()) dialog.dismiss();
        }
    }

    private WashiDialog() {}

    public static Dialog message(Context context, String title, String eyebrow, String message,
                                 boolean cancelable, Action... actions) {
        Dialog dialog = create(context, title, eyebrow, cancelable);
        LinearLayout content = dialog.findViewById(R.id.washiDialogContent);
        TextView body = body(context, message);
        body.setBackgroundResource(R.drawable.bg_dialog_note);
        body.setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12));
        content.addView(body);
        addActions(context, dialog, cancelable, actions);
        showSized(context, dialog);
        return dialog;
    }

    public static Dialog list(Context context, String title, String eyebrow, String[] labels,
                              SelectionListener onSelected) {
        Dialog dialog = create(context, title, eyebrow, true);
        LinearLayout content = dialog.findViewById(R.id.washiDialogContent);
        for (int i = 0; i < labels.length; i++) {
            final int selected = i;
            TextView row = new TextView(context);
            row.setText(labels[i]);
            row.setTextColor(color(context, R.color.text_primary));
            row.setTextSize(15);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinHeight(dp(context, 52));
            row.setPadding(dp(context, 14), dp(context, 10), dp(context, 14), dp(context, 10));
            row.setBackgroundResource(R.drawable.bg_dialog_list_item);
            row.setOnClickListener(v -> {
                dialog.dismiss();
                onSelected.onSelected(selected);
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) params.topMargin = dp(context, 8);
            row.setLayoutParams(params);
            content.addView(row);
        }
        addActions(context, dialog, true, Action.secondary("取消", null));
        showSized(context, dialog);
        return dialog;
    }

    public static ProgressHandle progress(Context context, String title, String eyebrow, String message) {
        Dialog dialog = create(context, title, eyebrow, false);
        LinearLayout content = dialog.findViewById(R.id.washiDialogContent);
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.bg_dialog_note);
        row.setPadding(dp(context, 14), dp(context, 14), dp(context, 14), dp(context, 14));

        CircularProgressIndicator indicator = new CircularProgressIndicator(context);
        indicator.setIndeterminate(true);
        indicator.setIndicatorColor(color(context, R.color.brand));
        indicator.setTrackColor(color(context, R.color.border_subtle));
        indicator.setIndicatorSize(dp(context, 30));
        indicator.setTrackThickness(dp(context, 3));
        row.addView(indicator, new LinearLayout.LayoutParams(dp(context, 40), dp(context, 40)));

        TextView body = body(context, message);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        bodyParams.setMarginStart(dp(context, 12));
        body.setLayoutParams(bodyParams);
        row.addView(body);
        content.addView(row);
        showSized(context, dialog);
        return new ProgressHandle(dialog, body);
    }

    private static Dialog create(Context context, String title, String eyebrow, boolean cancelable) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(LayoutInflater.from(context).inflate(R.layout.dialog_washi, null));
        dialog.setCancelable(cancelable);
        dialog.setCanceledOnTouchOutside(cancelable);
        TextView titleView = dialog.findViewById(R.id.washiDialogTitle);
        TextView eyebrowView = dialog.findViewById(R.id.washiDialogEyebrow);
        titleView.setText(title);
        if (!TextUtils.isEmpty(eyebrow)) {
            eyebrowView.setText(eyebrow);
            eyebrowView.setVisibility(View.VISIBLE);
        }
        return dialog;
    }

    private static void addActions(Context context, Dialog dialog, boolean dialogCancelable, Action... actions) {
        LinearLayout container = dialog.findViewById(R.id.washiDialogActions);
        for (int i = 0; i < actions.length; i++) {
            Action action = actions[i];
            FrameLayout row = new FrameLayout(context);
            MaterialButton button = new MaterialButton(context);
            button.setText(action.label);
            button.setTextSize(15);
            button.setAllCaps(false);
            button.setMinHeight(dp(context, 48));
            button.setCornerRadius(dp(context, 8));
            button.setInsetTop(0);
            button.setInsetBottom(0);
            if (action.primary) {
                button.setBackgroundTintList(ColorStateList.valueOf(color(context, R.color.brand)));
                button.setTextColor(color(context, R.color.text_on_brand));
            } else {
                button.setBackgroundTintList(ColorStateList.valueOf(color(context, R.color.bg_surface)));
                button.setTextColor(color(context, action.danger ? R.color.danger : R.color.brand));
                button.setStrokeColor(ColorStateList.valueOf(color(context,
                        action.danger ? R.color.danger : R.color.border_strong)));
                button.setStrokeWidth(dp(context, 1));
            }
            row.addView(button, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            CircularProgressIndicator indicator = new CircularProgressIndicator(context);
            indicator.setIndeterminate(true);
            indicator.setIndicatorSize(dp(context, 20));
            indicator.setTrackThickness(dp(context, 2));
            indicator.setIndicatorColor(color(context,
                    action.primary ? R.color.text_on_brand : R.color.brand));
            indicator.setVisibility(View.GONE);
            FrameLayout.LayoutParams indicatorParams = new FrameLayout.LayoutParams(dp(context, 24), dp(context, 24));
            indicatorParams.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
            indicatorParams.setMarginStart(dp(context, 16));
            row.addView(indicator, indicatorParams);

            button.setOnClickListener(v -> {
                if (action.asyncCallback != null) {
                    ActionHandle handle = new ActionHandle(dialog, container, button, indicator,
                            action.label, dialogCancelable);
                    handle.start();
                    try {
                        action.asyncCallback.run(handle);
                    } catch (RuntimeException error) {
                        handle.finish();
                        throw error;
                    }
                    return;
                }
                dialog.dismiss();
                if (action.callback != null) action.callback.run();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 48));
            if (i > 0) params.topMargin = dp(context, 8);
            row.setLayoutParams(params);
            container.addView(row);
        }
    }

    private static void setButtonsEnabled(View view, boolean enabled) {
        if (view instanceof MaterialButton) view.setEnabled(enabled);
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            setButtonsEnabled(group.getChildAt(i), enabled);
        }
    }

    private static TextView body(Context context, String message) {
        TextView body = new TextView(context);
        body.setText(message);
        body.setTextColor(color(context, R.color.text_secondary));
        body.setTextSize(15);
        body.setLineSpacing(0, 1.35f);
        return body;
    }

    private static void showSized(Context context, Dialog dialog) {
        dialog.setOnShowListener(ignored -> {
            Window window = dialog.getWindow();
            if (window == null) return;
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams params = window.getAttributes();
            int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
            params.width = Math.min((int) (screenWidth * 0.90f), dp(context, 520));
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            params.dimAmount = 0.42f;
            window.setAttributes(params);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            ScrollView scroll = dialog.findViewById(R.id.washiDialogScroll);
            int maxHeight = (int) (context.getResources().getDisplayMetrics().heightPixels * 0.52f);
            scroll.post(() -> {
                if (scroll.getHeight() > maxHeight) {
                    ViewGroup.LayoutParams scrollParams = scroll.getLayoutParams();
                    scrollParams.height = maxHeight;
                    scroll.setLayoutParams(scrollParams);
                }
            });
        });
        dialog.show();
    }

    private static int color(Context context, int resource) {
        return ContextCompat.getColor(context, resource);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}

package com.sssxzxz.productkeyboard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import java.io.InputStream;
import java.util.List;

public class ModelImportActivity extends Activity {
    private TextView status;
    private Button choose;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(28,60,28,28);
        TextView title = new TextView(this); title.setText("导入商品型号"); title.setTextSize(24); layout.addView(title);
        TextView hint = new TextView(this); hint.setText("选择表格后，选择型号所在列并核对预览。仅保存型号，不保存价格等其他列。导入成功后替换旧型号库。\n\n支持 .xlsx 表格；型号内不能包含空格或换行。\n"); layout.addView(hint);
        status = new TextView(this); status.setText("已保存 "+new Preferences(this).models().size()+" 个型号"); layout.addView(status);
        choose = new Button(this); choose.setText("选择型号表格"); choose.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT); intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"); startActivityForResult(intent,1);
        }); layout.addView(choose);
        Button back = new Button(this); back.setText("返回"); back.setOnClickListener(v -> finish()); layout.addView(back);
        ScrollView scroll = new ScrollView(this); scroll.addView(layout); setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v,insets) -> { layout.setPadding(28,insets.getSystemWindowInsetTop()+28,28,insets.getSystemWindowInsetBottom()+28); return insets; });
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request,result,data);
        if(request!=1 || result!=RESULT_OK || data==null || data.getData()==null) return;
        choose.setEnabled(false); status.setText("正在读取表格…");
        new Thread(() -> {
            try (InputStream input = getContentResolver().openInputStream(data.getData())) {
                List<ModelWorkbook.Column> columns = ModelWorkbook.read(input);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    choose.setEnabled(true); status.setText("请选择型号列");
                    String[] labels = new String[columns.size()];
                    for(int i=0;i<labels.length;i++) labels[i]=columns.get(i).label;
                    new AlertDialog.Builder(this).setTitle("哪一列是商品型号？").setItems(labels,(d,index) -> preview(columns.get(index))).setNegativeButton("取消",null).show();
                });
            } catch (Exception error) {
                runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) { choose.setEnabled(true); status.setText("读取失败，原型号库未更改。请选择未加密的 .xlsx 表格。"); } });
            }
        },"model-import").start();
    }
    private void preview(ModelWorkbook.Column column) {
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(32,12,32,12);
        CheckBox header = new CheckBox(this); header.setText("跳过首个非空单元格（列标题）");
        String first = column.values.get(0).toLowerCase(java.util.Locale.ROOT);
        header.setChecked(first.contains("型号") || first.equals("model") || first.equals("sku")); content.addView(header);
        TextView sample = new TextView(this); content.addView(sample);
        Runnable update = () -> {
            try {
                List<String> models = ModelWorkbook.models(column.values,header.isChecked());
                int skipped = column.values.size() - (header.isChecked()?1:0) - models.size();
                sample.setText("将保存 "+models.size()+" 个型号；跳过重复或无效项 "+skipped+" 个。\n\n"+String.join("、",models.subList(0,Math.min(12,models.size()))));
            } catch (IllegalArgumentException error) { sample.setText(error.getMessage()); }
        };
        header.setOnCheckedChangeListener((b,checked) -> update.run()); update.run();
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("核对型号").setView(content).setNegativeButton("取消",null).setPositiveButton("保存",null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                List<String> models = ModelWorkbook.models(column.values,header.isChecked());
                if(models.isEmpty()) { Toast.makeText(this,"没有有效型号，原型号库未更改",Toast.LENGTH_SHORT).show(); return; }
                new Preferences(this).models(models); status.setText("已保存 "+models.size()+" 个型号。返回原输入框即可使用。"); dialog.dismiss();
            } catch (IllegalArgumentException error) { Toast.makeText(this,error.getMessage(),Toast.LENGTH_LONG).show(); }
        })); dialog.show();
    }
}

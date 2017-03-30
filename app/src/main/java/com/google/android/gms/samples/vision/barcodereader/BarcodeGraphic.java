/*
 * Copyright (C) The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.gms.samples.vision.barcodereader;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.media.MediaPlayer;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;

import com.google.android.gms.samples.vision.barcodereader.ui.camera.GraphicOverlay;
import com.google.android.gms.vision.barcode.Barcode;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Graphic instance for rendering barcode position, size, and ID within an associated graphic
 * overlay view.
 */
class BarcodeGraphic extends GraphicOverlay.Graphic {
    private static final String TAG = "BarcodeGrahpic";
    private int mId;

    private static Paint rTxtP;
    private static Paint gTxtP;
    private static Paint mRectPaint;
    private static Paint mTextPaint;
    private static Paint gRectP;
    private static Paint bRectP;
    private static Paint rRectP;

    private volatile Barcode mBarcode;

    private MediaPlayer mp;
    private static Bounce bounce;

    static int step = 0;
    static int countCells = 0;
    static int countGoods = 0;

    private static String bcTray = "";
    private static String sTray = "";
    private static String bcGoods = "";
    private static int needGoods = 0;

    static JSONObject trays = null;
    static JSONObject route = null;
    static JSONArray aCells;
    static String bcPlace = null;

    private boolean mRaw;

    BarcodeGraphic(GraphicOverlay overlay) {
        super(overlay);

        // Теперь берем из настроек
        Resources resources = MainActivity.getContextOfApplication().getResources();
        Context applicationContext = MainActivity.getContextOfApplication();
        SharedPreferences sPref = PreferenceManager.getDefaultSharedPreferences(applicationContext);
        mRaw = sPref.getBoolean("raw", false);
        mRectPaint = new Paint();
        mRectPaint.setColor(Color.RED);
        mRectPaint.setStyle(Paint.Style.STROKE);
        mRectPaint.setStrokeWidth(4.0f);

        mTextPaint = new Paint();
        mTextPaint.setColor(Color.BLUE);
        mTextPaint.setTextSize(36.0f);

        rTxtP = new Paint();
        rTxtP.setColor(Color.RED);
        rTxtP.setTextSize(36.0f);
        rTxtP.setStrokeWidth(2.0f);
        rTxtP.setStyle(Paint.Style.STROKE);

        gTxtP = new Paint(rTxtP);
        gTxtP.setColor(Color.GREEN);

        gRectP = new Paint();
        gRectP.setStrokeWidth(1);
        gRectP.setStyle(Paint.Style.FILL_AND_STROKE);
        gRectP.setColor(Color.GREEN);
        gRectP.setAlpha(120);

        bRectP = new Paint(gRectP);
        bRectP.setColor(Color.BLUE);

        rRectP = new Paint(gRectP);
        rRectP.setColor(Color.RED);
        rRectP.setAlpha(80);

        mp = MediaPlayer.create(applicationContext, R.raw.shot);
        bounce = new Bounce(5);

    }

    public int getId() {
        return mId;
    }

    public void setId(int id) {
        this.mId = id;
    }

    Barcode getBarcode() {
        return mBarcode;
    }

    /**
     * Updates the barcode instance from the detection of the most recent frame.  Invalidates the
     * relevant portions of the overlay to trigger a redraw.
     */
    void updateItem(Barcode barcode) {
        mBarcode = barcode;
        postInvalidate();
    }

    /**
     * Draws the barcode annotations for position, size, and raw value on the supplied canvas.
     */
    @Override
    public void draw(Canvas canvas) {
        Barcode barcode = mBarcode;
        if (barcode == null) {
            return;
        }

        RectF rect = new RectF(barcode.getBoundingBox());
        rect.left = translateX(rect.left);
        rect.top = translateY(rect.top);
        rect.right = translateX(rect.right);
        rect.bottom = translateY(rect.bottom);

        if(mRaw){
            BarcodeCaptureActivity.tvSost.setText(barcode.rawValue);
            canvas.drawRect(rect, mRectPaint);
            return;
        }

        if (trays == null
                || aCells == null
                || bcPlace == null
                ) {
            Paint mTextStem = new Paint();
            mTextStem.setColor(Color.RED);
            mTextStem.setTextSize(36.0f);
            canvas.drawText("Нет данных AJAX", 20, 80, mTextStem);
            Log.d(TAG, "=+=== Нет данных AJAX =====  " + barcode.rawValue);
            return;
        }


        int bc = 999;
        JSONObject j = null;

        switch (step) {
            case 0:   // Выбираем лоток
                if (!trays.isNull(barcode.rawValue)) {
                    // лоток "наш"
                    try {
                        sTray = trays.getString(barcode.rawValue);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                    if (bounce.check(sTray)) { // Поймали
                        BarcodeCaptureActivity.tvSost.setText("Подойдите к ячейке");
                        BarcodeCaptureActivity.tvTray.setText(sTray);
                        BarcodeCaptureActivity.tvTray.setVisibility(View.VISIBLE);
                        BarcodeCaptureActivity.tvTrayT.setVisibility(View.VISIBLE);

                        try {
                            JSONObject item0 = aCells.getJSONObject(0);
                            BarcodeCaptureActivity.tvNeed.setText(item0.getString("name"));
                            route = item0.getJSONObject("route");
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        mp.start();
                        step = 1;
                        bcTray = barcode.rawValue;
                    } else { // Фильтруем дребезг
                        canvas.drawRect(rect, gRectP);
                        canvas.drawText("Доступный лоток " + sTray + bounce.getCount(), rect.left, rect.top - 24, gTxtP);
                    }
                } else {
                    // лоток "левый"
                    bounce.reset();
                    canvas.drawRect(rect, rRectP);
                    canvas.drawText("Этот лоток недоступен", rect.left, rect.top - 24, rTxtP);
                }
                break;


            case 1:   //Распознаем стрелки
                canvas.drawText(barcode.rawValue, rect.left, rect.bottom, mTextPaint);
                if (route != null && !route.isNull(barcode.rawValue)) {
                    try {
                        bc = route.getInt(barcode.rawValue);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    float a = rect.height() / 8;

                    Path path = new Path();
                    path.reset();

                    switch (bc) {
                        case 0:
                            if (bounce.check(barcode.rawValue)) { // Поймали
                                BarcodeCaptureActivity.tvCell.setText(BarcodeCaptureActivity.tvNeed.getText());
                                BarcodeCaptureActivity.tvCell.setVisibility(View.VISIBLE);
                                BarcodeCaptureActivity.tvCellT.setVisibility(View.VISIBLE);

                                mp.start();
                                step = 2;

                                try {
                                    JSONObject item0 = aCells.getJSONObject(0);
                                    BarcodeCaptureActivity.tvNeed.setText(item0.getString("name"));
                                    route = item0.getJSONObject("route");
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }

                            } else { // Фильтруем дребезг
                                canvas.drawCircle(rect.centerX(), rect.centerY(), rect.height() / 2, bRectP);
                                canvas.drawText("Целевая ячейка " + barcode.rawValue + bounce.getCount(), rect.left, rect.top - 24, gTxtP);
                            }
                            break;
                        case 1:
                            path.moveTo(rect.right + a, rect.centerY() + a); // 1
                            path.lineTo(rect.left + a, rect.centerY() + a); // 2
                            path.lineTo(rect.centerX(), rect.bottom); // 3
                            path.lineTo(rect.centerX() - a, rect.bottom + a); // 4

                            path.lineTo(rect.left - a, rect.centerY()); // 5

                            path.lineTo(rect.centerX() - a, rect.top - a); // 4
                            path.lineTo(rect.centerX(), rect.top); // 3
                            path.lineTo(rect.left + a, rect.centerY() - a); // 2
                            path.lineTo(rect.right + a, rect.centerY() - a); // 1

                            path.lineTo(rect.right + a, rect.centerY() + a); // 0
                            break;
                        case 2:
                            path.moveTo(rect.left - a, rect.centerY() - a); // 1
                            path.lineTo(rect.right - a, rect.centerY() - a); // 2
                            path.lineTo(rect.centerX(), rect.top); // 3
                            path.lineTo(rect.centerX() + a, rect.top - a); // 4

                            path.lineTo(rect.right + a, rect.centerY()); // 5

                            path.lineTo(rect.centerX() + a, rect.bottom + a); // 4
                            path.lineTo(rect.centerX(), rect.bottom); // 3
                            path.lineTo(rect.right - a, rect.centerY() + a); // 2
                            path.lineTo(rect.left - a, rect.centerY() + a); // 1

                            path.lineTo(rect.left - a, rect.centerY() - a); // 0
                            break;
                        case 3:
                            path.moveTo(rect.centerX() - a, rect.bottom + a); // 1
                            path.lineTo(rect.centerX() - a, rect.top + a); // 2
                            path.lineTo(rect.left, rect.centerY()); // 3
                            path.lineTo(rect.left, rect.centerY()); // 4
                            path.lineTo(rect.left - a, rect.centerY() - a); // 4
                            path.lineTo(rect.centerX(), rect.top - a); // 5
                            path.lineTo(rect.right + a, rect.centerY() - a); // 4
                            path.lineTo(rect.right, rect.centerY()); // 3
                            path.lineTo(rect.centerX() + a, rect.top + a); // 2
                            path.lineTo(rect.centerX() + a, rect.bottom + a); // 0
                            path.lineTo(rect.centerX() - a, rect.bottom + a); // 0
                            break;
                        case 4:
                            path.moveTo(rect.centerX() - a, rect.top - a);
                            path.lineTo(rect.centerX() + a, rect.top - a); // 1
                            path.lineTo(rect.centerX() + a, rect.bottom - a); // 2
                            path.lineTo(rect.right, rect.centerY());    // 3
                            path.lineTo(rect.right + a, rect.centerY() + a);  // 4
                            path.lineTo(rect.centerX(), rect.bottom + a);  // 5
                            path.lineTo(rect.left - a, rect.centerY() + a);
                            path.lineTo(rect.left, rect.centerY());
                            path.lineTo(rect.centerX() - a, rect.bottom - a);
                            path.lineTo(rect.centerX() - a, rect.top - a);
                            break;
                        default:
                    }
                    canvas.drawPath(path, bRectP);
                    return;
                }
                canvas.drawRect(rect, mRectPaint);
                canvas.drawText(barcode.rawValue, rect.left, rect.bottom, mTextPaint);
                break;
/*
            case 2:  // Распознаем товар
                if (BarcodeCaptureActivity.goods != null && !BarcodeCaptureActivity.goods.isNull(barcode.rawValue)) {
                    // есть код
                    if (BarcodeCaptureActivity.lLay.getVisibility() != View.VISIBLE) {

                        try {
                            String sbc = BarcodeCaptureActivity.goods.getString(barcode.rawValue);
                            j = new JSONObject(sbc);
                            if (!j.isNull("code"))
                                BarcodeCaptureActivity.tvCode.setText(j.getString("code"));
                            if (!j.isNull("brand"))
                                BarcodeCaptureActivity.tvBrand.setText(j.getString("brand"));
                            if (!j.isNull("article"))
                                BarcodeCaptureActivity.tvArt.setText(j.getString("article"));
                            if (!j.isNull("name"))
                                BarcodeCaptureActivity.tvName.setText(j.getString("name"));
                            if (!j.isNull("quantity")) {
                                BarcodeCaptureActivity.qNeed.setText("Необходимо " + j.getString("quantity") + " шт.");
                                needGoods = j.getInt("quantity");
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        BarcodeCaptureActivity.tvSost.setText("Положите товар в лоток");
                        BarcodeCaptureActivity.qReal.setText("");
                        mp.start();
                        bcGoods = barcode.rawValue;
                        step = 3;
                        BarcodeCaptureActivity.lLay.setVisibility(View.VISIBLE);
                        BarcodeCaptureActivity.lLay2.setVisibility(View.VISIBLE);
//          canvas.drawBitmap(BitmapFactory.decodeResource( resources, R.mipmap.png2),rect.left,rect.top,null);
                    }
                    return;
                }
                break;

            case 3:  // Наполняем лоток (лоток)
                if (bcTray.equals(barcode.rawValue)) {
                    if (bounce.check(bcTray)) { // Поймали
                        mp.start();
                        countGoods++;
                        if (countGoods == needGoods) {
                            step = 5;
                            mp.start();
                            BarcodeCaptureActivity.tvSost.setText("Доставте лоток в зону погрузки.");
                            BarcodeCaptureActivity.lLay.setVisibility(View.INVISIBLE);
                            BarcodeCaptureActivity.lLay2.setVisibility(View.INVISIBLE);
                        } else {
                            step = 4;
                            BarcodeCaptureActivity.tvSost.setText("Возьмите товар с ячейки");
                            BarcodeCaptureActivity.qReal.setText("Выбрано " + countGoods + " шт.");
                        }
                    } else { // Фильтруем дребезг
                        canvas.drawRect(rect, gRectP);
                        canvas.drawText("Наш лоток " + sTray + bounce.getCount(), rect.left, rect.top - 24, gTxtP);
                    }
                } else {
                    bounce.reset();
                    canvas.drawRect(rect, mRectPaint);
                    canvas.drawText(barcode.rawValue, rect.left, rect.bottom, mTextPaint);
                }
                break;

            case 4:  // Наполняем лоток (ячейка)
                if (bcGoods.equals(barcode.rawValue)) {
                    if (bounce.check(bcGoods)) { // Поймали
                        BarcodeCaptureActivity.tvSost.setText("Положите товар в лоток");
                        mp.start();
                        step = 3;
                    } else { // Фильтруем дребезг
                        canvas.drawRect(rect, gRectP);
                        canvas.drawText("Наш товар" + bounce.getCount(), rect.left, rect.top - 24, gTxtP);
                    }
                } else {
                    bounce.reset();
                    canvas.drawRect(rect, mRectPaint);
                    canvas.drawText(barcode.rawValue, rect.left, rect.bottom, mTextPaint);
                }
                break;

*/
            case 5:  // Выходим в зону погрузки
                if (bcPlace.equals(barcode.rawValue)) {
                    if (bounce.check(bcPlace)) { // Поймали
                        BarcodeCaptureActivity.tvSost.setText("Поздравляем, Вы справились с заданием.");
                        mp.start();
                        step = 6;
                    } else { // Фильтруем дребезг
                        canvas.drawRect(rect, gRectP);
                        canvas.drawText("Зона погрузки" + bounce.getCount(), rect.left, rect.top - 24, gTxtP);
                    }
                } else {
                    bounce.reset();
                    canvas.drawRect(rect, mRectPaint);
                    canvas.drawText(barcode.rawValue, rect.left, rect.top - 24, mTextPaint);
                }
                break;
            default:
        }
    }

    private static class Bounce {
        private static int maxBounce;
        private static int count;
        private static String str;

        Bounce(int max) {
            maxBounce = max;
            count = max;
            str = "";
        }

        void reset() {
            str = "";
            count = maxBounce;
        }

        boolean check(String bc) {
            if (count == maxBounce) {
                str = bc;
                count--;
                return false;
            }
            if (str.equals(bc)) {
                if (count-- == 0) {
                    reset();
                    return true;
                }
            } else {
                reset();
            }
            return false;
        }

        String getCount() {
            return " (" + count + ") ";
        }

    }
}

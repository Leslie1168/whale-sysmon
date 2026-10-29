package com.whale.sysmon.data;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.util.Size;

import java.util.ArrayList;
import java.util.List;

/**
 * 相机：数量、各镜头分辨率上限、光圈与 OIS。
 */
public final class Camera {

    public static final class Cam {
        public String id = "--";
        public String facing = "--";
        public String maxPhoto = "--";
        public String maxVideo = "--";
        public String aperture = "--";
        public boolean ois;
        public String flash = "--";
        public String hdr;
    }

    public final List<Cam> cams = new ArrayList<Cam>();
    public boolean available;

    public static Camera read(Context ctx) {
        Camera c = new Camera();
        try {
            CameraManager cm = (CameraManager) ctx.getSystemService(Context.CAMERA_SERVICE);
            if (cm == null) return c;
            String[] ids = cm.getCameraIdList();
            c.available = ids.length > 0;
            for (String id : ids) {
                try {
                    CameraCharacteristics ch = cm.getCameraCharacteristics(id);
                    Cam cam = new Cam();
                    cam.id = id;
                    Integer facing = ch.get(CameraCharacteristics.LENS_FACING);
                    cam.facing = facing == null ? "--"
                            : (facing == CameraCharacteristics.LENS_FACING_BACK ? "后置"
                            : (facing == CameraCharacteristics.LENS_FACING_FRONT ? "前置" : "外接"));

                    StreamConfigurationMap map =
                            ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    if (map != null) {
                        Size[] jpeg = map.getOutputSizes(ImageFormat.JPEG);
                        if (jpeg != null && jpeg.length > 0) {
                            Size best = jpeg[0];
                            long maxPx = 0;
                            for (Size s : jpeg) {
                                long px = (long) s.getWidth() * s.getHeight();
                                if (px > maxPx) { maxPx = px; best = s; }
                            }
                            cam.maxPhoto = best.getWidth() + "×" + best.getHeight()
                                    + "  (" + mp(best.getWidth() * (long) best.getHeight()) + ")";
                        }
                        Size[] vid = map.getOutputSizes(android.media.MediaRecorder.class);
                        if (vid != null && vid.length > 0) {
                            Size best = vid[0];
                            long maxPx = 0;
                            for (Size s : vid) {
                                long px = (long) s.getWidth() * s.getHeight();
                                if (px > maxPx) { maxPx = px; best = s; }
                            }
                            cam.maxVideo = best.getWidth() + "×" + best.getHeight();
                        }
                    }

                    float[] ap = ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES);
                    if (ap != null && ap.length > 0) {
                        cam.aperture = "f/" + trim(ap[0]);
                    }
                    int[] ois = ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION);
                    cam.ois = ois != null && ois.length > 0;
                    Boolean flash = ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                    cam.flash = flash != null && flash ? "有" : "无";
                    int[] hdr = ch.get(CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES);
                    cam.hdr = hdr == null ? "--" : "支持 " + hdr.length + " 种场景模式";

                    c.cams.add(cam);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return c;
    }

    private static String mp(long px) {
        double m = px / 1000000.0;
        return String.format(java.util.Locale.US, "%.0f MP", m);
    }

    private static String trim(float f) {
        String s = String.format(java.util.Locale.US, "%.2f", f);
        if (s.endsWith("0")) s = s.substring(0, s.length() - 1);
        if (s.endsWith("0")) s = s.substring(0, s.length() - 1);
        if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        return s;
    }
}

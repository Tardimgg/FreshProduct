package com.example.freshproduct.addProduct.makePhoto;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.SoundEffectConstants;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.CalendarView;
import android.widget.DatePicker;
import android.widget.Toast;

import com.example.freshproduct.Result;
import com.example.freshproduct.databinding.FragmentMakePhotoBinding;
import com.example.freshproduct.productInfoLoader.ProductInfoLoader;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Objects;

public class MakePhotoFragment extends Fragment implements ImageReader.OnImageAvailableListener {


    private static final String PRODUCT_INFO_PARAM = "PRODUCT_INFO_PARAM";
    private static final String PHOTO_MAKING_PARAM = "PHOTO_MAKING_PARAM";
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    FragmentMakePhotoBinding binding;
    private static WindowInsetsCompat insetsBtn;
    private boolean requiredStopWorking = false;
    private Toast toast;


    // TODO: Rename and change types of parameters
    private ProductReadingListener productReadingListener;
    private CompletingPhotoMakingListener completingPhotoMakingListener;
    private ImageReader imageReader;

    public MakePhotoFragment() {
        // Required empty public constructor
    }

    public static MakePhotoFragment newInstance(ProductReadingListener productReadingListener,
                                                CompletingPhotoMakingListener completingPhotoMakingListener) {

        MakePhotoFragment fragment = new MakePhotoFragment();
        Bundle args = new Bundle();
        args.putSerializable(PRODUCT_INFO_PARAM, productReadingListener);
        args.putSerializable(PHOTO_MAKING_PARAM, completingPhotoMakingListener);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            this.productReadingListener = (ProductReadingListener) getArguments().getSerializable(PRODUCT_INFO_PARAM);
            this.completingPhotoMakingListener = (CompletingPhotoMakingListener) getArguments().getSerializable(PHOTO_MAKING_PARAM);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_EXTERNAL_STORAGE && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            createPreview();
        }
    }

    public void setInsets(WindowInsetsCompat insets) {
        MakePhotoFragment.insetsBtn = insets;
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentMakePhotoBinding.inflate(inflater, container, false);

        if (insetsBtn != null) {
            binding.endPhotoMaking.setTranslationY(-insetsBtn.getSystemWindowInsets().bottom);
            binding.endPhotoMaking.setTranslationX(-insetsBtn.getSystemWindowInsets().right);
        }

        binding.endPhotoMaking.setOnClickListener((v) -> {
            if (completingPhotoMakingListener != null) {
                if (inWork) {
                    if (toast != null) {
                        toast.cancel();
                    }
                    toast = Toast.makeText(getContext(), "Пожалуйста подождите", Toast.LENGTH_SHORT);
                    toast.show();
                    requiredStopWorking = true;
                } else {
                    synchronized (productReadingListener) {
                        if (blockingPhotoCreations) {
                            Date date = new GregorianCalendar(binding.expirationDate.getYear(),
                                    binding.expirationDate.getMonth(),
                                    binding.expirationDate.getDayOfMonth()
                            ).getTime();
                            productReadingListener.productReadyEvent(lastRes, date.getTime());
                            blockingPhotoCreations = false;
                        }
                    }
                    completingPhotoMakingListener.completingPhotoMakingEvent();
                }
            }
        });


        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        binding.expirationDate.init(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH), new DatePicker.OnDateChangedListener() {

            private int numberRequirementsHide = 0;

            @Override
            public void onDateChanged(DatePicker datePicker, int year, int month, int dayOfMonth) {
                if (datePicker.getVisibility() == View.VISIBLE) {
                    ++numberRequirementsHide;
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (numberRequirementsHide == 1) {
                            binding.expirationDate.animate()
                                    .withEndAction(() -> binding.expirationDate.setVisibility(View.INVISIBLE))
                                    .alpha(0f)
                                    .setInterpolator(new AccelerateDecelerateInterpolator())
                                    .setDuration(300)
                                    .start();
                            if (productReadingListener != null) {
                                synchronized (productReadingListener) {
                                    if (blockingPhotoCreations) {
                                        Date date = new GregorianCalendar(year, month, dayOfMonth).getTime();
                                        productReadingListener.productReadyEvent(lastRes, date.getTime());
                                        blockingPhotoCreations = false;
                                    }
                                }
                            }
                            blockingPhotoCreations = false;
                        }
                        --numberRequirementsHide;
                    }, 2000);
                }

            }
        });

        return binding.getRoot();
    }

    private HandlerThread backgroundCameraThread;
    private Handler backgroundCameraHandler = null;

    private void startBackgroundThread() {
        backgroundCameraThread = new HandlerThread("CameraBackground");
        backgroundCameraThread.start();
        backgroundCameraHandler = new Handler(backgroundCameraThread.getLooper());
    }

    private void stopBackgroundThread() {
        backgroundCameraThread.quitSafely();
        try {
            backgroundCameraThread.join();
            backgroundCameraThread = null;
            backgroundCameraHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void onPause() {
        super.onPause();
        stopBackgroundThread();
    }

    @Override
    public void onResume() {
        super.onResume();
        startBackgroundThread();
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) !=
                PackageManager.PERMISSION_GRANTED) {

            requestPermissions(new String[]{Manifest.permission.CAMERA},
                    REQUEST_EXTERNAL_STORAGE);
        } else {
            createPreview();
        }
    }



    private void createPreview() {

        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED) {

            try {
                CameraManager manager = (CameraManager) requireContext().getSystemService(Context.CAMERA_SERVICE);

                String[] list = manager.getCameraIdList();

                float focusDistance = manager.getCameraCharacteristics(list[0])
                        .get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);

                manager.openCamera(list[0], new CameraDevice.StateCallback() {
                    @Override
                    public void onOpened(@NonNull CameraDevice cameraDevice) {
                        imageReader = ImageReader.newInstance(1280, 720, ImageFormat.YUV_420_888, 2);
                        imageReader.setOnImageAvailableListener(MakePhotoFragment.this, backgroundCameraHandler);
                        if (binding.cameraView.isAvailable()) {
                            configureCamera(cameraDevice, binding.cameraView.getSurfaceTexture(), focusDistance);
                        } else {
                            binding.cameraView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                                @Override
                                public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int i, int i1) {
                                    configureCamera(cameraDevice, surfaceTexture, focusDistance);
                                }

                                @Override
                                public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surfaceTexture, int i, int i1) {

                                }

                                @Override
                                public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surfaceTexture) {
                                    return false;
                                }

                                @Override
                                public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {

                                }
                            });
                        }
                    }

                    @Override
                    public void onDisconnected(@NonNull CameraDevice cameraDevice) {
                    }

                    @Override
                    public void onError(@NonNull CameraDevice cameraDevice, int i) {
                    }
                }, backgroundCameraHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }

    private void configureCamera(CameraDevice cameraDevice, SurfaceTexture texture, float focusDistance) {
        texture.setDefaultBufferSize(1280, 720);
        Surface surface = new Surface(texture);

        try {
            final CaptureRequest.Builder builder =
                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(surface);
            Surface mImageSurface = imageReader.getSurface();
            builder.addTarget(mImageSurface);

            builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, focusDistance);
            builder.set(CaptureRequest.CONTROL_AF_MODE, 0);


            cameraDevice.createCaptureSession(Arrays.asList(surface, imageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            try {
                                cameraCaptureSession.setRepeatingRequest(builder.build(),
                                        null, backgroundCameraHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                        }
                    }, backgroundCameraHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    private boolean inWork = false;
    private boolean afterDownloadProcess = false;
    private long endTime = 0;
    private final long DELAY_FOR_LOAD = (long) Math.pow(10, 9);
    private final long DELAY_FOR_SCAN = (long) Math.pow(10, 8);
    private boolean blockingPhotoCreations = false;
    private Result<Pair<String, String>, String> lastRes;

    @Override
    public synchronized void onImageAvailable(ImageReader imageReader) {
        long currentTime = System.nanoTime();
        if (!requiredStopWorking && !inWork &&
                (!afterDownloadProcess && currentTime - endTime > DELAY_FOR_SCAN ||
                        afterDownloadProcess && currentTime - endTime > DELAY_FOR_LOAD)) {

            inWork = true;
            afterDownloadProcess = false;
            Image image = imageReader.acquireNextImage();
            if (image != null) {

                BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(Barcode.FORMAT_EAN_13).build();
                BarcodeScanner scanner = BarcodeScanning.getClient(options);
                scanner.process(InputImage.fromMediaImage(image, 0)).addOnCompleteListener(task -> {
                    List<Barcode> list = task.getResult();
                    if (list.size() > 0) {

                        if (blockingPhotoCreations) {
                            if (toast != null) {
                                toast.cancel();
                            }
                            toast = Toast.makeText(getContext(), "Укажите срок годности предыдущего продукта", Toast.LENGTH_SHORT);
                            toast.show();
                            inWork = false;

                        } else {
                            requireView().playSoundEffect(SoundEffectConstants.CLICK);
                            new ProductInfoLoader(res -> {

                                blockingPhotoCreations = true;
                                lastRes = res;

                                if (toast != null) {
                                    toast.cancel();
                                }
                                if (res.isHaveValue) {
                                    if (res.value.first.isEmpty()) {
                                        toast = Toast.makeText(getContext(), res.value.second, Toast.LENGTH_SHORT);
                                    } else {
                                        toast = Toast.makeText(getContext(), res.value.first, Toast.LENGTH_SHORT);
                                    }
                                } else {
                                    toast = Toast.makeText(getContext(), res.error, Toast.LENGTH_SHORT);
                                }
                                toast.show();


                                Calendar calendar = Calendar.getInstance();
                                calendar.setTimeInMillis(System.currentTimeMillis());
                                binding.expirationDate.updateDate(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
                                binding.expirationDate.animate()
                                        .withStartAction(() -> binding.expirationDate.setVisibility(View.VISIBLE))
                                        .alpha(1f)
                                        .setInterpolator(new AccelerateDecelerateInterpolator())
                                        .setDuration(300)
                                        .start();

                                inWork = false;
                                afterDownloadProcess = true;
                                endTime = System.nanoTime();

                            }).execute(list.get(0).getDisplayValue());
                        }

                    } else {
                        inWork = false;
                        endTime = System.nanoTime();
                    }
                    image.close();
                });
            }

        } else {
            if (imageReader != null) {
                imageReader.acquireNextImage().close();
            }
        }
        if (requiredStopWorking && !inWork) {
            synchronized (productReadingListener) {
                if (blockingPhotoCreations) {
                    Date date = new GregorianCalendar(binding.expirationDate.getYear(),
                            binding.expirationDate.getMonth(),
                            binding.expirationDate.getDayOfMonth()
                    ).getTime();
                    productReadingListener.productReadyEvent(lastRes, date.getTime());
                    blockingPhotoCreations = false;
                }
            }
            completingPhotoMakingListener.completingPhotoMakingEvent();
        }
    }
}
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
import androidx.core.view.ViewCompat;
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
import android.widget.DatePicker;
import android.widget.Toast;

import com.example.freshproduct.GlobalInsets;
import com.example.freshproduct.R;
import com.example.freshproduct.Result;
import com.example.freshproduct.databinding.FragmentMakePhotoBinding;
import com.example.freshproduct.models.ArrayResponse;
import com.example.freshproduct.models.Receipt;
import com.example.freshproduct.productInfoLoader.ProductInfoLoader;
import com.example.freshproduct.webApi.SingleWebApi;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.reactivex.disposables.Disposable;
import io.reactivex.observers.DisposableSingleObserver;
import io.reactivex.schedulers.Schedulers;

public class MakePhotoFragment extends Fragment implements ImageReader.OnImageAvailableListener {


    private static final String PRODUCT_INFO_PARAM = "PRODUCT_INFO_PARAM";
    private static final String PHOTO_MAKING_PARAM = "PHOTO_MAKING_PARAM";
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    FragmentMakePhotoBinding binding;
    private boolean requiredStopWorking = false;
    private Toast toast;


    private ProductReadingListener productReadingListener;
    private CompletingPhotoMakingListener completingPhotoMakingListener;
    private ImageReader imageReader;

    Disposable insetsListener;


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

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (insetsListener != null) {
            insetsListener.dispose();
        }
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentMakePhotoBinding.inflate(inflater, container, false);

        insetsListener = GlobalInsets.getInstance().subscribeToInsets(insets -> {

            binding.endPhotoMaking.setTranslationY(-insets.getSystemWindowInsets().bottom);
            binding.endPhotoMaking.setTranslationX(-insets.getSystemWindowInsets().right);

        });

        ViewCompat.setOnApplyWindowInsetsListener(binding.endPhotoMaking, (v, insets) -> {
            GlobalInsets.getInstance().setInsets(insets);
            return insets;
        });

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
//                        imageReader = ImageReader.newInstance(720, 1280, ImageFormat.YUV_420_888, 2);
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
//        texture.setDefaultBufferSize(1280, 1280);
//        texture.setDefaultBufferSize(720, 1280);
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
                            } catch (CameraAccessException | IllegalArgumentException e) {
                                Toast.makeText(getContext(), "camera error", Toast.LENGTH_LONG).show();
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

    Pattern fnPattern = Pattern.compile("fn=(.+?)&");
    Pattern fdPattern = Pattern.compile("i=(.+?)&");
    Pattern fpPattern = Pattern.compile("fp=(.+?)&");
    Pattern totalSumPattern = Pattern.compile("s=(.+?)&");
    Pattern datePattern = Pattern.compile("t=(.+?)T");
    Pattern timePattern = Pattern.compile("T(.+?)&");


    Pattern firstWordPattern = Pattern.compile("(.+?\\s)");


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
                        .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_QR_CODE).build();
                BarcodeScanner scanner = BarcodeScanning.getClient(options);
                scanner.process(InputImage.fromMediaImage(image, 0)).addOnCompleteListener(task -> {
                    List<Barcode> list = task.getResult();
                    if (list.size() > 0 && list.get(0).getRawValue() != null) {

                        if (blockingPhotoCreations) {
                            if (toast != null) {
                                toast.cancel();
                            }
                            toast = Toast.makeText(getContext(), "Укажите срок годности предыдущего продукта", Toast.LENGTH_SHORT);
                            toast.show();
                            inWork = false;

                        } else {
                            requireView().playSoundEffect(SoundEffectConstants.CLICK);

                            Barcode barcode = list.get(0);

                            switch (barcode.getFormat()) {
                                case Barcode.FORMAT_QR_CODE:
                                    String qr = barcode.getRawValue();

                                    Matcher fnMatcher = fnPattern.matcher(qr);
                                    boolean fnRes = fnMatcher.find();

                                    Matcher fdMatcher = fdPattern.matcher(qr);
                                    boolean fdRes = fdMatcher.find();

                                    Matcher fpMatcher = fpPattern.matcher(qr);
                                    boolean fpRes = fpMatcher.find();

                                    Matcher totalSumMatcher = totalSumPattern.matcher(qr);
                                    boolean totalSumRes = totalSumMatcher.find();

                                    Matcher dateMatcher = datePattern.matcher(qr);
                                    boolean dateRes = dateMatcher.find();

                                    Matcher timeMatcher = timePattern.matcher(qr);
                                    boolean timeRes = timeMatcher.find();

                                    if (fnRes && fdRes && fpRes && totalSumRes && dateRes && timeRes) {
                                        Receipt receipt = new Receipt();

                                        receipt.fn_param = fnMatcher.group(1);
                                        receipt.fd = fdMatcher.group(1);
                                        receipt.fp = fpMatcher.group(1);
                                        receipt.total_sum = totalSumMatcher.group(1);

                                        String date = dateMatcher.group(1);

                                        receipt.date = String.format("%s.%s.%s", date.substring(6), date.substring(4, 6), date.substring(0, 4));

                                        String time = timeMatcher.group(1);
                                        receipt.time = String.format("%s:%s", time.substring(0, 2), time.substring(2, 4));

                                        receipt.receipt_type = "Приход";

                                        SingleWebApi.getInstance().getReceiptInfo(receipt)
                                                .subscribeOn(Schedulers.newThread())
                                                .observeOn(Schedulers.io())
                                                .subscribe(new DisposableSingleObserver<ArrayResponse>() {
                                                    @Override
                                                    public void onSuccess(ArrayResponse arrayResponse) {
                                                        for (String receipt : arrayResponse.response) {

                                                            Matcher matcher = firstWordPattern.matcher(receipt);
                                                            if (matcher.find()) {
                                                                productReadingListener.productReadyEvent(
                                                                        Result.some(Pair.create(matcher.group(1), receipt)), -1
                                                                );
                                                            }
                                                        }

                                                        Log.e("load receipt info", arrayResponse.response.get(0));
                                                        inWork = false;
                                                        afterDownloadProcess = true;
                                                        endTime = System.nanoTime();

                                                        requireActivity().runOnUiThread(() -> {
                                                            binding.loadInfo.setVisibility(View.INVISIBLE);
                                                            binding.loadReceiptInfoBar.setVisibility(View.INVISIBLE);

                                                            binding.loadReceiptOk.setImageResource(R.drawable.ok);
                                                            binding.loadReceiptOk.setVisibility(View.VISIBLE);

                                                            binding.loadReceiptInfo.animate()
                                                                    .setStartDelay(600)
                                                                    .withStartAction(() -> binding.loadReceiptInfo.setVisibility(View.VISIBLE))
                                                                    .alpha(0f)
                                                                    .setInterpolator(new AccelerateDecelerateInterpolator())
                                                                    .setDuration(300)
                                                                    .start();

                                                            binding.loadReceiptOk.animate()
                                                                    .setStartDelay(900)
                                                                    .withStartAction(() -> binding.loadReceiptOk.setVisibility(View.INVISIBLE))
                                                                    .start();


                                                            binding.loadInfo.setText(null);
                                                        });

                                                    }

                                                    @Override
                                                    public void onError(Throwable e) {
                                                        requireActivity().runOnUiThread(() -> {
                                                            Toast.makeText(getContext(), "Попробуйте в следующий раз", Toast.LENGTH_LONG).show();
                                                            Log.e("load receipt info", e.toString());
                                                            inWork = false;
                                                            afterDownloadProcess = true;
                                                            endTime = System.nanoTime();

                                                            binding.loadInfo.setVisibility(View.INVISIBLE);
                                                            binding.loadReceiptInfoBar.setVisibility(View.INVISIBLE);

                                                            binding.loadReceiptOk.setImageResource(R.drawable.error);
                                                            binding.loadReceiptOk.setVisibility(View.VISIBLE);

                                                            binding.loadReceiptInfo.animate()
                                                                    .setStartDelay(600)
                                                                    .withStartAction(() -> binding.loadReceiptInfo.setVisibility(View.VISIBLE))
                                                                    .alpha(0f)
                                                                    .setInterpolator(new AccelerateDecelerateInterpolator())
                                                                    .setDuration(300)
                                                                    .start();

                                                            binding.loadReceiptOk.animate()
                                                                    .setStartDelay(900)
                                                                    .withStartAction(() -> binding.loadReceiptOk.setVisibility(View.INVISIBLE))
                                                                    .start();


                                                            binding.loadInfo.setText(null);

                                                        });
                                                    }
                                                });
                                    }
                                    binding.loadReceiptInfoBar.setVisibility(View.VISIBLE);
                                    binding.loadInfo.setVisibility(View.VISIBLE);
                                    binding.loadReceiptInfo.animate()
                                            .withStartAction(() -> binding.loadReceiptInfo.setVisibility(View.VISIBLE))
                                            .alpha(1f)
                                            .setInterpolator(new AccelerateDecelerateInterpolator())
                                            .setDuration(300)
                                            .start();

                                    binding.loadInfo.setText("Загрузка чека");

                                    break;
                                case Barcode.FORMAT_EAN_13:
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

                                    }).execute(barcode.getDisplayValue());
                            }
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
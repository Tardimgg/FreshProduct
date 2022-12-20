package com.example.freshproduct;

import static com.example.freshproduct.MathFunc.convertDpToPixel;

import android.animation.ValueAnimator;
import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.compose.ui.text.input.ImeOptions;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.example.freshproduct.databinding.FragmentAuthBinding;
import com.example.freshproduct.models.SimpleResponse;
import com.example.freshproduct.models.User;
import com.example.freshproduct.webApi.SingleWebApi;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Observer;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.observers.DisposableSingleObserver;
import io.reactivex.schedulers.Schedulers;

public class AuthFragment extends Fragment {

    enum AuthStates {
        LOG_IN,
        SIGN_UP
    }


    FragmentAuthBinding binding;

    private SimpleObservable authCompleted;
    private SimpleObservable needRegistration;

    private static final int ANIMATION_DURATION = 600;

    private AuthStates currentStates = AuthStates.LOG_IN;


    public AuthFragment() {

        authCompleted = new SimpleObservable();
        needRegistration = new SimpleObservable();

    }

    public void subscribeToCompleteAuth(Observer observer) {
        authCompleted.addObserver(observer);
    }

    public void subscribeToNeedRegistration(Observer observer) {
        needRegistration.addObserver(observer);
    }

    public static AuthFragment newInstance() {
        return new AuthFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (CurrentUser.getInstance(getContext()).getLogin() != null && CurrentUser.getInstance(getContext()).getPassword() != null) {
            authCompleted.notifyObservers();
        }

    }

    Disposable insetsListener;


    @Override
    public void onDestroy() {
        super.onDestroy();

        if (insetsListener != null) {
            insetsListener.dispose();
        }
    }

    public void setLogInMode() {
        if (currentStates != AuthStates.LOG_IN) {
            currentStates = AuthStates.LOG_IN;
            transformationIntoLogIn(ANIMATION_DURATION);
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        binding = FragmentAuthBinding.inflate(inflater, container, false);

//        binding.passwordEdit.setBackgroundResource(R.drawable.password_during_registration);
//        binding.password.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_FILLED);


        binding.errorView.setTextColor(binding.passwordRepetition.getBoxStrokeErrorColor().getDefaultColor());

        binding.withoutRegistration.setOnClickListener((v) -> {
            new MaterialAlertDialogBuilder(requireContext(), R.style.AlertDialogTheme)
                    .setTitle("Регистрация в FreshProduct")
                    .setMessage("Регистрироваться очень важно. Точно. Много текста. Очень много")
//                    .setMessage("ваще круть ахуеешь. оч харашо тьочно текта пака нет но точно ахуеешь ваще супер круто точно блеять. " +
//                            "тут должно быть очень много текста оч важного прям совсем так что бы точно захател и " +
//                            "еще инфа где потом можно зарегистрироваться и чтоб удобно")
                    .setPositiveButton("Зарегистрироваться", (dialogInterface, i) ->
                            registrationClickListener.onClick(binding.registration))
                    .setNeutralButton("Продолжить без регистрации", (dialogInterface, i) -> {
                        CurrentUser currentUser = CurrentUser.getInstance(getContext());
                        currentUser.setLogin(null);
                        currentUser.setPassword(null);

                        authCompleted.notifyObservers();
                    }).show();
        });

        binding.auth.setOnClickListener((v) -> {

            View view = requireActivity().getCurrentFocus();
            if (view != null) {
                InputMethodManager imm = (InputMethodManager) requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }

            binding.login.setError(null);
            binding.password.setError(null);
            binding.errorView.setText("");
            binding.auth.setEnabled(false);

            String login = binding.login.getEditText().getText().toString();
            String password = binding.password.getEditText().getText().toString();

            SingleWebApi.getInstance()
                    .checkRegistration(new User(login, password))
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new DisposableSingleObserver<SimpleResponse>() {
                        @Override
                        public void onSuccess(SimpleResponse status) {
                            switch (status.response) {
                                case "user not found":
                                    binding.login.setError(" ");
                                    binding.errorView.setText("Такой пользователь не существует");
                                    break;

                                case "invalid password":
                                    binding.password.setError(" ");
                                    binding.errorView.setText("Пароль неверный");
                                    break;

                                case "registered":
                                    CurrentUser currentUser = CurrentUser.getInstance(getContext());
                                    currentUser.setLogin(login);
                                    currentUser.setPassword(password);

                                    authCompleted.notifyObservers();
                                    break;

                                default:
                                    Toast.makeText(requireContext(), "the server returned an incorrect response",
                                            Toast.LENGTH_LONG).show();
                            }
                            binding.auth.setEnabled(true);
                        }

                        @Override
                        public void onError(Throwable e) {
                            // ДАБАВИТЬ ПРАВЕРКУ НА ТО ШО НЕПРАВИЛЬНАЯЯЯЯЯ!!!!1

                            binding.auth.setEnabled(true);
                            binding.errorView.setText("Повторите попытку позже");
                            Log.e("check registration", e.toString());
                        }
                    });
        });

        binding.registration.setOnClickListener(registrationClickListener);

        GlobalInsets globalInsets = GlobalInsets.getInstance();

        insetsListener = globalInsets.subscribeToInsets(insets -> {

            binding.registration.setTranslationY(-insets.getSystemWindowInsets().bottom);
            binding.registration.setTranslationX(-insets.getSystemWindowInsets().right);

            binding.withoutRegistration.setTranslationY(-insets.getSystemWindowInsets().bottom);
            binding.withoutRegistration.setTranslationX(-insets.getSystemWindowInsets().right);

            if (currentStates == AuthStates.SIGN_UP) {
                animationTransitionToRegistration(0);
            }
        });

        ViewCompat.setOnApplyWindowInsetsListener(binding.withoutRegistration, (v, insets) -> {
            GlobalInsets.getInstance().setInsets(insets);
            return insets;
        });

        ViewCompat.setOnApplyWindowInsetsListener(binding.registration, (v, insets) -> {
            GlobalInsets.getInstance().setInsets(insets);
            return insets;
        });

        return binding.getRoot();

    }

    private final RegistrationClickListener registrationClickListener = new RegistrationClickListener();

    private class RegistrationClickListener implements View.OnClickListener {

        @Override
        public void onClick(View view) {

            binding.login.setError(null);
            binding.password.setError(null);
            binding.passwordRepetition.setError(null);

            if (currentStates == AuthStates.SIGN_UP) {
                String login = binding.login.getEditText().getText().toString();
                String password = binding.password.getEditText().getText().toString();
                String passwordRepetition = binding.passwordRepetition.getEditText().getText().toString();

                boolean haveEmptyFields = false;
                if (login.trim().equals("")) {
                    binding.login.setError(" ");
                    haveEmptyFields = true;
                }
                if (password.trim().equals("")) {
                    binding.password.setError(" ");
                    haveEmptyFields = true;
                }
                if (passwordRepetition.trim().equals("")) {
                    binding.passwordRepetition.setError(" ");
                    haveEmptyFields = true;
                }

                if (haveEmptyFields) {
//                    binding.passwordRepetition.setErrorIconDrawable(null);
//                    binding.passwordRepetition.setHintTextColor(ColorStateList.valueOf(Color.BLACK));

                    binding.errorView.setText(getResources().getText(R.string.empty_fields));
//                    binding.passwordRepetition.setError(getResources().getText(R.string.empty_fields));
//                    binding.errorView.setText("лох");
                } else {

                    if (!password.trim().equals("") && password.equals(binding.passwordRepetition.getEditText().getText().toString())) {
                        binding.registration.setEnabled(false);
                        SingleWebApi.getInstance().register(new User(binding.login.getEditText().getText().toString(), binding.password.getEditText().getText().toString()))
                                .subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(new DisposableSingleObserver<SimpleResponse>() {

                                    @Override
                                    public void onSuccess(SimpleResponse status) {

                                        switch (status.response) {
                                            case "this login already exists":
                                                binding.login.setError(" ");
                                                binding.errorView.setText("Введённый логин занят");
                                                binding.registration.setEnabled(true);
                                                break;

                                            case "success":
                                                CurrentUser currentUser = CurrentUser.getInstance(getContext());
                                                currentUser.setLogin(login);
                                                currentUser.setPassword(password);

                                                authCompleted.notifyObservers();
                                                break;

                                            default:
                                                Toast.makeText(requireContext(), "the server returned an incorrect response",
                                                        Toast.LENGTH_LONG).show();
                                                binding.errorView.setText("Обновите приложение");

                                        }
                                    }

                                    @Override
                                    public void onError(Throwable e) {
                                        Log.e("registration error", e.toString());
                                        Toast.makeText(requireContext(), "Проблемы с интернетам и мб и нет", Toast.LENGTH_LONG).show();
                                        binding.registration.setEnabled(true);

                                        binding.errorView.setText("Повторите попытку позже");

                                    }
                                });
                    } else {

                        binding.password.setError(" ");
                        binding.passwordRepetition.setError(" ");
                        binding.errorView.setText(getResources().getText(R.string.password_mismatch));
//                    binding.passwordRepetition.setErrorTextColor(ColorStateList.valueOf(Color.RED));
                    }
                }

            } else {
                currentStates = AuthStates.SIGN_UP;

                transformationIntoRegistration(ANIMATION_DURATION);

            }
        }
    }

    /*
    private void restoringTransitions() {

        binding.registration.setTranslationY(0);
        binding.withoutRegistration.setTranslationY(0);
        binding.auth.setTranslationX(0);
        binding.passwordRepetition.setTranslationY(0);

        animationTransitionToRegistration(0);
    }
     */

    private void animationTransitionToRegistration(int duration) {
        GlobalInsets globalInsets = GlobalInsets.getInstance();

        int bottomInsets = globalInsets.getInsets().getSystemWindowInsets().bottom;
        int buttonHeight = (int) convertDpToPixel(50, requireContext());
        float bottomMargin = convertDpToPixel(25, requireContext());
        binding.registration
                .animate()
//                .translationY(bottomInsets - bottomMargin)
                .translationY(-bottomInsets + bottomMargin + buttonHeight)
                .setDuration(duration)
                .start();

        binding.withoutRegistration
                .animate()
//                .translationY(bottomInsets + bottomMargin)
                .translationY(buttonHeight + bottomMargin)
                .setDuration(duration)
                .alpha(0)
                .start();

        binding.auth
                .animate()
                .translationX(convertDpToPixel(49, requireContext()))
                .setDuration(duration)
                .alpha(0)
                .start();

        binding.passwordRepetition
                .animate()
                .translationY(convertDpToPixel(-60, requireContext()))
                .alpha(1)
                .setDuration(0)
                .start();

        binding.passwordRepetition
                .animate()
                .translationY(0)
                .setDuration(duration)
                .start();

        binding.errorView
                .animate()
                .translationY(convertDpToPixel(60, requireContext()))
                .setDuration(duration)
                .start();
    }

    private void transformationIntoRegistration(int duration) {

        Animation inAnimation = AnimationUtils.loadAnimation(getContext(),
                android.R.anim.fade_in);
        Animation outAnimation = AnimationUtils.loadAnimation(getContext(),
                android.R.anim.fade_out);
        binding.textSwitcher.setInAnimation(inAnimation);
        binding.textSwitcher.setOutAnimation(outAnimation);

        binding.textSwitcher.setText(getResources().getText(R.string.registration));
        binding.registration.setText(getResources().getText(R.string.register));

        binding.withoutRegistration.setEnabled(false);
        binding.auth.setEnabled(false);

        ValueAnimator widthAnimator = ValueAnimator.ofInt(49, 21);
        widthAnimator.addUpdateListener(animationAuthData);
        widthAnimator.setDuration(duration);
        widthAnimator.start();

        int pL = binding.passwordEdit.getPaddingLeft();
        int pT = binding.passwordEdit.getPaddingTop();
        int pR = binding.passwordEdit.getPaddingRight();
        int pB = binding.passwordEdit.getPaddingBottom();
//        binding.passwordEdit.setBackgroundResource(R.drawable.without_rounding);
        binding.passwordEdit.setPadding(pL, pT, pR, pB);

        binding.passwordEdit.setImeOptions(EditorInfo.IME_ACTION_NEXT);

        binding.login.setError(null);
        binding.password.setError(null);
        binding.errorView.setText("");


        binding.passwordRepetition.setError(null);
        binding.passwordRepetition.setEnabled(true);
        binding.passwordRepetition.setVisibility(View.VISIBLE);

        animationTransitionToRegistration(duration);

    }

    private final ValueAnimator.AnimatorUpdateListener animationAuthData = valueAnimator -> {
        ViewGroup.LayoutParams previousParams = binding.login.getLayoutParams();
        RelativeLayout.LayoutParams marginParams = new RelativeLayout.LayoutParams(previousParams);
        marginParams.setMarginStart(((RelativeLayout.LayoutParams) previousParams).leftMargin);
        marginParams.setMarginEnd((int) convertDpToPixel((Integer) valueAnimator.getAnimatedValue(), requireContext()));
        binding.login.setLayoutParams(marginParams);

        previousParams = binding.password.getLayoutParams();
        marginParams = new RelativeLayout.LayoutParams(previousParams);
        marginParams.setMarginStart(((RelativeLayout.LayoutParams) previousParams).leftMargin);
        marginParams.setMarginEnd((int) convertDpToPixel((Integer) valueAnimator.getAnimatedValue(), requireContext()));
        marginParams.addRule(RelativeLayout.BELOW, R.id.login);
        binding.password.setLayoutParams(marginParams);

        previousParams = binding.passwordRepetition.getLayoutParams();
        marginParams = new RelativeLayout.LayoutParams(previousParams);
        marginParams.setMarginStart(((RelativeLayout.LayoutParams) previousParams).leftMargin);
        marginParams.setMarginEnd((int) convertDpToPixel((Integer) valueAnimator.getAnimatedValue(), requireContext()));
        marginParams.addRule(RelativeLayout.BELOW, R.id.password);
        binding.passwordRepetition.setLayoutParams(marginParams);

        previousParams = binding.errorView.getLayoutParams();
        marginParams = new RelativeLayout.LayoutParams(previousParams);
        marginParams.setMarginStart(((RelativeLayout.LayoutParams) previousParams).leftMargin);
        marginParams.setMarginEnd((int) convertDpToPixel((Integer) valueAnimator.getAnimatedValue(), requireContext()));
        int leftM = ((RelativeLayout.LayoutParams) previousParams).leftMargin;
        int rightM = (int) convertDpToPixel((Integer) valueAnimator.getAnimatedValue(), requireContext());
        int bottomM = ((RelativeLayout.LayoutParams) previousParams).bottomMargin;
        marginParams.setMargins(leftM, (int) convertDpToPixel(60, requireContext()), rightM, bottomM);
        marginParams.addRule(RelativeLayout.BELOW, R.id.login);
        binding.errorView.setLayoutParams(marginParams);
    };

    private void animationTransitionToLogIn(int duration) {
        GlobalInsets globalInsets = GlobalInsets.getInstance();

        int bottomInsets = globalInsets.getInsets().getSystemWindowInsets().bottom;
        binding.registration
                .animate()
                .translationY(-bottomInsets)
                .setDuration(duration)
                .start();

        binding.withoutRegistration
                .animate()
                .translationY(-bottomInsets)
                .setDuration(duration)
                .alpha(1)
                .start();

        binding.auth
                .animate()
                .translationX(0)
                .setDuration(duration)
                .alpha(1)
                .start();

        binding.passwordRepetition
                .animate()
                .translationY(convertDpToPixel(-60, requireContext()))
                .setDuration(duration)
                .start();

//        binding.passwordRepetition
//                .animate()
//                .setStartDelay(duration)
//                .withStartAction(() -> binding.passwordRepetition.setVisibility(View.INVISIBLE))
//                .translationY(0)
//                .setDuration(0)
//                .start();
//
        binding.errorView
                .animate()
                .translationY(0)
                .setDuration(duration)
                .start();
    }

    private void transformationIntoLogIn(int duration) {

        Animation inAnimation = AnimationUtils.loadAnimation(getContext(),
                android.R.anim.fade_in);
        Animation outAnimation = AnimationUtils.loadAnimation(getContext(),
                android.R.anim.fade_out);
        binding.textSwitcher.setInAnimation(inAnimation);
        binding.textSwitcher.setOutAnimation(outAnimation);

        binding.textSwitcher.setText(getResources().getText(R.string.entrance));
        binding.registration.setText(getResources().getText(R.string.registration));

        binding.withoutRegistration.setEnabled(true);
        binding.auth.setEnabled(true);

        ValueAnimator widthAnimator = ValueAnimator.ofInt(21, 49);
        widthAnimator.addUpdateListener(animationAuthData);
        widthAnimator.setDuration(duration);
        widthAnimator.start();

//        Handler handler = new Handler();
//        handler.postDelayed(() -> {
//            int pL = binding.passwordEdit.getPaddingLeft();
//            int pT = binding.passwordEdit.getPaddingTop();
//            int pR = binding.passwordEdit.getPaddingRight();
//            int pB = binding.passwordEdit.getPaddingBottom();
//            binding.passwordEdit.setBackgroundResource(R.drawable.rounded_bottom);
//            binding.passwordEdit.setBackgroundResource(R.drawable.without_rounding);
//            binding.passwordEdit.setPadding(pL, pT, pR, pB);
//        }, duration * 3L /4);

        binding.passwordEdit.setImeOptions(EditorInfo.IME_ACTION_DONE);



        binding.login.setError(null);
        binding.password.setError(null);

        binding.errorView.setText("");

        binding.passwordRepetition.setError(null);
        binding.passwordRepetition.setEnabled(false);
        binding.passwordRepetition.getEditText().getText().clear();


        animationTransitionToLogIn(duration);

    }


}
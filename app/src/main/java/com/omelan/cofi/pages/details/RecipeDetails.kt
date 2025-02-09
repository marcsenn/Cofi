@file:OptIn(
    ExperimentalMaterial3WindowSizeClassApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalAnimationApi::class,
)

package com.omelan.cofi.pages.details

import android.os.Build
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAndroidRect
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.omelan.cofi.LocalPiPState
import com.omelan.cofi.R
import com.omelan.cofi.components.*
import com.omelan.cofi.model.DataStore
import com.omelan.cofi.model.NEXT_STEP_ENABLED_DEFAULT_VALUE
import com.omelan.cofi.share.*
import com.omelan.cofi.share.timer.Timer
import com.omelan.cofi.share.utils.getActivity
import com.omelan.cofi.ui.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun RecipeDetails(
    recipeId: Int,
    isInPiP: Boolean = LocalPiPState.current,
    onRecipeEnd: (Recipe) -> Unit = {},
    goToEdit: () -> Unit = {},
    goBack: () -> Unit = {},
    onTimerRunning: (Boolean) -> Unit = { },
    stepsViewModel: StepsViewModel = viewModel(),
    recipeViewModel: RecipeViewModel = viewModel(),
    windowSizeClass: WindowSizeClass = WindowSizeClass.calculateFromSize(DpSize(1920.dp, 1080.dp)),
) {
    val steps by stepsViewModel.getAllStepsForRecipe(recipeId).observeAsState(listOf())
    val recipe by recipeViewModel.getRecipe(recipeId)
        .observeAsState(Recipe(name = "", description = ""))
    RecipeDetails(
        recipe,
        steps,
        isInPiP,
        onRecipeEnd,
        goToEdit,
        goBack,
        onTimerRunning,
        windowSizeClass,
    )
}

@Composable
fun RecipeDetails(
    recipe: Recipe,
    steps: List<Step>,
    isInPiP: Boolean = LocalPiPState.current,
    onRecipeEnd: (Recipe) -> Unit = {},
    goToEdit: () -> Unit = {},
    goBack: () -> Unit = {},
    onTimerRunning: (Boolean) -> Unit = { },
    windowSizeClass: WindowSizeClass = WindowSizeClass.calculateFromSize(DpSize(1920.dp, 1080.dp)),
) {
    val recipeId by remember(recipe) {
        derivedStateOf { recipe.id }
    }

    val coroutineScope = rememberCoroutineScope()
    val (appBarBehavior, collapse) = createAppBarBehaviorWithCollapse()
    val snackbarState = remember { SnackbarHostState() }
    val copyAutomateLink = rememberCopyAutomateLink(snackbarState, recipeId)

    val lazyListState = rememberLazyListState()

    val dataStore = DataStore(LocalContext.current)
    val combineWeightState by dataStore.getWeightSetting()
        .collectAsState(initial = COMBINE_WEIGHT_DEFAULT_VALUE)
    val isNextStepEnabled by dataStore.getNextStepSetting()
        .collectAsState(initial = NEXT_STEP_ENABLED_DEFAULT_VALUE)

    var showAutomateLinkDialog by remember { mutableStateOf(false) }

    val weightMultiplier = remember { mutableStateOf(1.0f) }
    val timeMultiplier = remember { mutableStateOf(1.0f) }
    var ratioSheetIsVisible by remember {
        mutableStateOf(false)
    }

    val (
        currentStep,
        isDone,
        isTimerRunning,
        indexOfCurrentStep,
        animatedProgressValue,
        animatedProgressColor,
        pauseAnimations,
        progressAnimation,
        startAnimations,
        changeToNextStep,
    ) = Timer.createTimerControllers(
        steps = steps,
        onRecipeEnd = { onRecipeEnd(recipe) },
        dataStore = dataStore,
        doneTrackColor = MaterialTheme.colorScheme.primary,
        timeMultiplier = timeMultiplier.value,
    )

    val nextStep = remember(indexOfCurrentStep, steps) {
        if (steps.isEmpty() || indexOfCurrentStep == -1 || indexOfCurrentStep == steps.lastIndex) {
            null
        } else {
            steps[indexOfCurrentStep + 1]
        }
    }

    val alreadyDoneWeight by Timer.rememberAlreadyDoneWeight(
        indexOfCurrentStep = indexOfCurrentStep,
        allSteps = steps,
        combineWeightState = combineWeightState,
        weightMultiplier = weightMultiplier.value,
    )

    LaunchedEffect(currentStep.value) {
        progressAnimation(Unit)
    }
    LaunchedEffect(isTimerRunning) {
        onTimerRunning(isTimerRunning)
    }

    LaunchedEffect(key1 = recipe) {
        lazyListState.animateScrollToItem(0)
    }
    DisposableEffect(true) {
        onDispose {
            onTimerRunning(false)
        }
    }
    LaunchedEffect(ratioSheetIsVisible) {
        if (ratioSheetIsVisible && isTimerRunning) {
            pauseAnimations()
        }
    }

    suspend fun startRecipe() = coroutineScope.launch {
        collapse()
        launch {
            lazyListState.animateScrollToItem(if (recipe.description.isNotBlank()) 1 else 0)
        }
        launch {
            changeToNextStep(true)
        }
    }

    val isPhoneLayout = rememberIsPhoneLayout(windowSizeClass)

    val renderDescription: @Composable ((Modifier) -> Unit)? =
        if (recipe.description.isNotBlank()) {
            {
                Description(
                    modifier = it
                        .fillMaxWidth()
                        .testTag("recipe_description"),
                    descriptionText = recipe.description,
                )
                Spacer(modifier = Modifier.height(Spacing.big))
            }
        } else {
            null
        }

    val renderTimer: @Composable (Modifier) -> Unit = {
        val activity = LocalContext.current.getActivity()
        Timer(
            modifier = Modifier
                .testTag("recipe_timer")
                .onGloballyPositioned { coordinates ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        coroutineScope.launch(Dispatchers.IO) {
                            setPiPSettings(
                                activity,
                                isTimerRunning,
                                coordinates
                                    .boundsInWindow()
                                    .roundToIntRect()
                                    .toAndroidRect(),
                            )
                        }
                    }
                },
            currentStep = currentStep.value,
            allSteps = steps,
            animatedProgressValue = animatedProgressValue,
            animatedProgressColor = animatedProgressColor,
            isInPiP = isInPiP,
            alreadyDoneWeight = alreadyDoneWeight,
            isDone = isDone,
            weightMultiplier = weightMultiplier.value,
            timeMultiplier = timeMultiplier.value,
        )
        if (!isInPiP) {
            Spacer(modifier = Modifier.height(Spacing.big))
        }
    }
    val renderUpNext: LazyListScope.() -> Unit = {
        item("up_next") {
            AnimatedVisibility(
                nextStep != null && !isInPiP && isNextStepEnabled,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
            ) {
                UpNext(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItemPlacement()
                        .padding(bottom = Spacing.normal),
                    step = nextStep ?: Step(name = "", type = StepType.WAIT),
                )
            }
        }
    }

    val renderSteps: LazyListScope.() -> Unit = {
        itemsIndexed(items = steps, key = { _, step -> step.id }) { index, step ->
            StepListItem(
                modifier = Modifier
                    .testTag("recipe_step"),
                step = step,
                stepProgress = when {
                    index < indexOfCurrentStep -> StepProgress.Done
                    indexOfCurrentStep == index -> StepProgress.Current
                    else -> StepProgress.Upcoming
                },
                onClick = { newStep: Step ->
                    coroutineScope.launch {
                        if (newStep == currentStep.value) {
                            return@launch
                        }
                        animatedProgressValue.snapTo(0f)
                        currentStep.value = (newStep)
                    }
                },
                weightMultiplier = weightMultiplier.value,
                timeMultiplier = timeMultiplier.value,
            )
            Divider()
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(appBarBehavior.nestedScrollConnection),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarState,
                modifier = Modifier.padding(Spacing.medium),
            ) {
                Snackbar {
                    Text(text = it.visuals.message)
                }
            }
        },
        topBar = {
            PiPAwareAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = recipe.recipeIcon.icon),
                            contentDescription = null,
                            modifier = Modifier.padding(end = Spacing.small),
                        )
                        Text(
                            text = recipe.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = goBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { ratioSheetIsVisible = true }) {
                        Icon(
                            painterResource(id = R.drawable.ic_scale),
                            contentDescription = null,
                        )
                    }
                    IconButton(onClick = { showAutomateLinkDialog = true }) {
                        Icon(
                            painterResource(id = R.drawable.ic_link),
                            contentDescription = null,
                        )
                    }
                    IconButton(onClick = goToEdit) {
                        Icon(
                            painterResource(id = R.drawable.ic_edit),
                            contentDescription = null,
                        )
                    }
                },
                scrollBehavior = appBarBehavior,
            )
        },
        floatingActionButton = {
            if (!isInPiP) {
                StartFAB(
                    isTimerRunning = isTimerRunning,
                    onClick = {
                        if (currentStep.value != null) {
                            if (animatedProgressValue.isRunning) {
                                coroutineScope.launch { pauseAnimations() }
                            } else {
                                coroutineScope.launch {
                                    if (currentStep.value?.time == null) {
                                        changeToNextStep(false)
                                    } else {
                                        startAnimations()
                                    }
                                }
                            }
                            return@StartFAB
                        }
                        coroutineScope.launch { startRecipe() }
                    },
                )
            }
        },
        floatingActionButtonPosition = if (isPhoneLayout) {
            FabPosition.Center
        } else {
            FabPosition.End
        },
    ) {
        if (ratioSheetIsVisible) {
            RatioBottomSheet(
                timeMultiplier = timeMultiplier,
                weightMultiplier = weightMultiplier,
                onDismissRequest = {
                    ratioSheetIsVisible = false
                },
            )
        }
        if (isPhoneLayout) {
            PhoneLayout(
                it,
                renderDescription,
                renderTimer,
                renderUpNext,
                renderSteps,
                isInPiP,
                lazyListState,
            )
        } else {
            TabletLayout(it, renderDescription, renderTimer, renderUpNext, renderSteps, isInPiP)
        }
    }

    if (showAutomateLinkDialog) {
        DirectLinkDialog(
            dismiss = { showAutomateLinkDialog = false },
            onConfirm = {
                copyAutomateLink()
                showAutomateLinkDialog = false
            },
        )
    }
}

@Preview(showBackground = true)
@Composable
fun RecipeDetailsPreview() {
    RecipeDetails(
        recipeId = 1,
        isInPiP = false,
    )
}

@Preview(showBackground = true)
@Composable
fun RecipeDetailsPreviewPip() {
    RecipeDetails(
        recipeId = 1,
        isInPiP = true,
    )
}

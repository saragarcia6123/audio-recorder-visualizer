@Composable
fun ComposableChatMicPanel(
    modifier: Modifier = Modifier,
    isRecording: Boolean,
    onTap: () -> Unit,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    onDrag: () -> Unit,
    setRecordPermissionGranted: (Boolean) -> Unit,
    isMessageTextEmpty: Boolean,
    coroutineScope: CoroutineScope
) {

    var micButtonSize = (LocalConfiguration.current.screenWidthDp*.3f).dp
    micButtonSize = 200.coerceAtMost(micButtonSize.value.toInt()).dp

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted: Boolean ->
            if (isGranted) {
                setRecordPermissionGranted(true)
                PermissionRecordAudio.isGranted()
            } else {
                setRecordPermissionGranted(false)
                PermissionRecordAudio.isDenied()
            }
        }
    )

    val lockPanelWidth = micButtonSize * .8f
    val lockPanelHeight = micButtonSize * .5f

    val lockButtonXDistance = lockPanelWidth - lockPanelHeight / 2

    var swipeOffsetX by remember { mutableStateOf(0f) }

    var isLocked by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(isRecording) {
        if (!isRecording) isLocked = false
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = micButtonSize)
            .padding(start = lockPanelWidth)
    ) {
        ComposableChatMicButton(
            modifier = Modifier
                .zIndex(1f)
                .pointerInput(Unit) {
                    awaitEachGesture {

                        if (isMessageTextEmpty) {
                            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }

                        awaitFirstDown(requireUnconsumed = false)

                        val longPressJob = coroutineScope.launch {
                            delay(800)
                            onPress()
                        }

                        var upConsumed: Boolean
                        do {
                            val event = awaitPointerEvent()
                            upConsumed = event.changes.any { it.changedToUpIgnoreConsumed() }
                            if (upConsumed) {
                                longPressJob.cancel()
                                onTap()
                            }
                        } while (!upConsumed)

                        onRelease()
                    }
                }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        swipeOffsetX += delta
                        if (swipeOffsetX.dp > lockButtonXDistance) {
                            isLocked = true
                            onDrag()
                            swipeOffsetX = 0f
                        }
                    }
                ),
            size = micButtonSize,
            isRecording = isRecording
        )

        ComposableChatMicLock(
            modifier = Modifier
                .height(lockPanelHeight)
                .width(lockPanelWidth)
                .offset(x = (-4).dp),
            isLocked = isLocked,
            isVisible = isRecording && recordMethod == EntityLocalRecordMethodHold,
        )
    }
}

@Preview
@Composable
fun ComposableChatMicPanelPreview() {
    recordMethod = EntityLocalRecordMethodHold
    ComposableChatMicPanel(
        isRecording = true,
        onTap = {},
        onPress = {},
        onRelease = {},
        onDrag = {},
        coroutineScope = rememberCoroutineScope(),
        isMessageTextEmpty = true,
        setRecordPermissionGranted = {}
    )
}
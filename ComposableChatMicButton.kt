@Composable
fun ComposableChatMicButton(
    size: Dp,
    modifier: Modifier = Modifier,
    isRecording: Boolean
) {

    val standardColor = Brush.verticalGradient(colors = listOf(
        ColorsLight.standard.primary,
        ColorsLight.standard.onPrimaryContainer
    ))

    val recordingColor = Brush.verticalGradient(colors = listOf(
        ColorsLight.standard.error,
        ColorsLight.standard.onErrorContainer
    ))

    var backgroundColor by remember {
        mutableStateOf(
            if (isRecording) {
                recordingColor
            } else {
                standardColor
            }
        )
    }

    val radialGradient = Brush.radialGradient(listOf(
        Color.White,
        Color.Transparent
    ), radius = 10f)

    val noGradient = Brush.radialGradient(listOf(
        Color.Transparent,
        Color.Transparent
    ), radius = 10f)

    var radialBackground by remember { mutableStateOf(radialGradient) }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            backgroundColor = recordingColor
            radialBackground = radialGradient
        } else {
            backgroundColor = standardColor
            radialBackground = noGradient
        }

    }

    Box(modifier = Modifier
        .size(size)
            .clip(CircleShape)
            .background(radialBackground)){
        Box(
            modifier = Modifier
                .padding(6.dp)
                .fillMaxSize()
                .aspectRatio(1f)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                shape = CircleShape,
                modifier = modifier
                    .clip(CircleShape)
                    .border(
                        shape = CircleShape,
                        border = BorderStroke(
                            width = 5.dp,
                            brush = Brush.verticalGradient(
                                listOf(Color.White, Color.White)
                            )
                        )
                    )
                    .background(backgroundColor)
                    .align(Alignment.Center)

            ) {
                Image(
                    colorFilter = ColorFilter.tint(Color.White),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(5.dp),
                    imageVector = Icons.Default.Mic,
                    contentDescription = if (isRecording) {
                        language.translations.screenMain.stopRecordingButtonContentDescription
                    } else {
                        language.translations.screenMain.startRecordingButtonContentDescription
                    }
                )
            }
        }
    }
}

@Preview
@Composable
fun ComposableMicButtonPreview() {
    ComposableChatMicButton(
        size = 64.dp,
        isRecording = false,
    )
}
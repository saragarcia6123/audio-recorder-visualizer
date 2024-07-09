@Composable
fun ComposableChatMicLock(
    modifier: Modifier = Modifier,
    isVisible: Boolean,
    isLocked: Boolean
) {
    var alpha by remember { mutableStateOf(if (isVisible) { 1f } else { 0f }) }

    LaunchedEffect(isVisible) {
        alpha = if (isVisible) { 1f } else { 0f }
    }

    Row(
        modifier = modifier
            .width(100.dp)
            .height(40.dp)
            .alpha(alpha)
    ) {
        Canvas(
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .fillMaxHeight()
                .weight(2f)
                .padding(top = 2.dp, end = 4.dp)
        ) {

            drawLine(
                pathEffect = if (isLocked) null else PathEffect
                    .dashPathEffect(floatArrayOf(
                        size.width*.18f,
                        size.width*.5f
                    ), 0f),
                strokeWidth = size.height*.15f,
                color = Color.White,
                start = Offset(-4f,size.height/2f),
                end = Offset(size.width+12, size.height/2f),
                cap = StrokeCap.Round
            )
        }

        val imageVector = if (isLocked) {
            Icons.Outlined.Lock
        } else {
            Icons.Outlined.LockOpen
        }

        Box(
            modifier = Modifier
                .padding(2.dp)
                .drawBehind {
                    drawCircle(
                        color = Color.White, style = Stroke(
                            cap = StrokeCap.Round,
                            width = 12f,
                            pathEffect = if (isLocked) {
                                null
                            } else {
                                PathEffect.dashPathEffect(
                                    floatArrayOf(
                                        size.width * .25f,
                                        size.width * .2f
                                    ), 4f
                                )
                            }
                        )
                    )
                }
                .border(
                    width = 2.dp,
                    color = if (isLocked) Color.White else Color.Transparent,
                    shape = CircleShape
                )
                .align(Alignment.CenterVertically)
                .background(
                    shape = CircleShape,
                    color = if (isLocked)
                        Color(180, 255, 200, 130)
                    else
                        Color.Transparent
                )
        ){
            Image(
                colorFilter = ColorFilter.tint(Color.White),
                imageVector = imageVector,
                contentDescription = "",
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f)
                    .padding(8.dp)
            )
        }
    }
}

@Preview
@Composable
fun ComposableChatMicLockPreview() {
    Surface(
        color = Color.Black
    ) {

        ComposableChatMicLock(
            modifier = Modifier
                .height(50.dp)
                .width(80.dp),
            isVisible = true,
            isLocked = true
        )
    }
}
@Composable
fun ComposableAmplitudesBar(
    magnitudes: FloatArray,
    modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = modifier
            .wrapContentHeight()
            .fillMaxWidth()
    ) {
        DynamicAmplitudeBars(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .wrapContentHeight(),
            amplitudes = magnitudes
        )
    }
}

@Composable
private fun DynamicAmplitudeBars(
    amplitudes: FloatArray,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
    ) {
        amplitudes.forEach { magnitude ->
            AmplitudeBar(
                magnitude = magnitude,
                barWidth = 8.dp
            )
        }
    }
}

@Composable
private fun AmplitudeBar(
    magnitude: Float,
    barWidth: Dp,
    modifier: Modifier = Modifier
) {

    Canvas(
        modifier = modifier
            .width(barWidth*2)
            .fillMaxHeight()
            .padding(vertical = 16.dp)
    ) {

        val centerY = size.height/2

        drawLine(
            strokeWidth = size.width,
            color = Color.White,
            start = Offset(size.width/2, (centerY-magnitude*(size.height/2f))),
            end = Offset(size.width/2, (centerY+magnitude*(size.height/2f))),
            cap = StrokeCap.Round
        )
    }
}

@Preview
@Composable
fun MeterPreview() {
    ComposableAmplitudesBar(
        modifier = Modifier
            .height(200.dp),
        magnitudes = (FloatArray(10) { 1f })
    )
}
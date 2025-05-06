package com.example.demo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get
import androidx.core.graphics.scale
import com.example.demo.ui.theme.DemoTheme
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DemoTheme {
                DrawingScreen()
            }
        }
    }
}

@Composable
fun DrawingScreen() {
    val lines = remember { mutableStateListOf<Line>() }
    val density = LocalDensity.current
    val context = LocalContext.current
    Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { start ->
                            lines.add(Line(start, start))
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val line = Line(
                                start = change.position - dragAmount,
                                end = change.position
                            )
                            lines.add(line)
                        }
                    )
                }
        ) {
            lines.forEach { line ->
                drawLine(
                    color = line.color,
                    start = line.start,
                    end = line.end,
                    strokeWidth = with(density) { line.strokeWidth.toPx() },
                    cap = StrokeCap.Round
                )
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp, 50.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(onClick = { saveDrawing(lines, context) }) {
                Text("Save")
            }
            Button(onClick = { lines.clear() }) {
                Text("Clear")
            }
            Button(onClick = {
                val savedFile = saveDrawing(lines, context)
                if (savedFile != null) {
                    val recognizer = HandwritingRecognizer(context)
                    val result = recognizer.recognizeHandwriting(savedFile)
                    Toast.makeText(context, "Recognized: $result", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Error saving drawing.", Toast.LENGTH_LONG).show()
                }
            }) {
                Text("Recognize")
            }
        }
    }
}

fun saveDrawing(lines: List<Line>, context: Context): File? {
    val bitmap = createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)
    val paint = android.graphics.Paint().apply {
        color = android.graphics.Color.BLACK
        strokeWidth = 10f
        strokeCap = android.graphics.Paint.Cap.ROUND
    }
    lines.forEach { line ->
        canvas.drawLine(
            line.start.x, line.start.y, line.end.x, line.end.y, paint
        )
    }
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val fileName = "Drawing_$timestamp.png"
    val file = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), fileName)
    return try {
        val outputStream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        outputStream.flush()
        outputStream.close()
        Toast.makeText(context, "Drawing saved successfully!", Toast.LENGTH_SHORT).show()
        file
    } catch (e: IOException) {
        Log.e("SaveDrawing", "Error saving file", e)
        Toast.makeText(context, "Error saving file: ${e.message}", Toast.LENGTH_LONG).show()
        null
    }
}

data class Line(
    val start: Offset,
    val end: Offset,
    val color: Color = Color.Black,
    val strokeWidth: androidx.compose.ui.unit.Dp = 3.dp
)

class HandwritingRecognizer(context: Context) {
    private val interpreter: Interpreter? = try {
        val model = loadModelFile(context)
        if (model != null) Interpreter(model) else null
    } catch (e: Exception) {
        Log.e("Interpreter", "Error initializing interpreter", e)
        null
    }
    fun recognizeHandwriting(imageFile: File): String {
        return try {
            if (interpreter == null) return "Model not loaded"
            val input = preprocessImage(imageFile)
            val inputArray = arrayOf(input)
            val outputArray = Array(1) { FloatArray(10) }
            interpreter.run(inputArray, outputArray)
            outputArray.contentDeepToString()
        } catch (e: Exception) {
            Log.e("HandwritingRecognizer", "Recognition Failed", e)
            "Recognition Failed"
        }
    }
}

fun preprocessImage(file: File): FloatArray {
    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
    val resizedBitmap = bitmap.scale(28, 28) // Resize to model input size
    val floatArray = FloatArray(28 * 28)
    for (y in 0 until 28) {
        for (x in 0 until 28) {
            val pixel = resizedBitmap[x, y]
            val grayscale = (android.graphics.Color.red(pixel) +
                    android.graphics.Color.green(pixel) +
                    android.graphics.Color.blue(pixel)) / 3f // Convert to grayscale
            floatArray[y * 28 + x] = grayscale / 255f // Normalize to 0-1
        }
    }
    return floatArray
}

fun loadModelFile(context: Context): MappedByteBuffer? {
    return try {
        val fileDescriptor = context.assets.openFd("math_recognition.tflite")
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
    } catch (e: IOException) {
        Log.e("LoadModel", "Error loading model", e)
        null
    }
}

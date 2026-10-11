package com.teppe21.finances.feature.receipt

import com.teppe21.finances.feature.categories.accountLabel
import android.Manifest
import android.content.pm.PackageManager
import kotlin.math.abs
import java.util.concurrent.Executors
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.teppe21.finances.core.ui.Motion
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.teppe21.finances.R
import com.teppe21.finances.core.theme.finances
import com.teppe21.finances.FinancesApp
import com.teppe21.finances.core.money.Money
import com.teppe21.finances.core.utils.CurrencyFormatter
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanReceiptScreen(
    onNavigateBack: () -> Unit,
    viewModel: ReceiptViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.uiState.collectAsState()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.processUri(uri)
        }
    }

    val view = LocalView.current
    val haptics = LocalHapticFeedback.current
    val imageCapture = remember { ImageCapture.Builder().build() }
    val captureExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose { captureExecutor.shutdown() }
    }


    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (hasCameraPermission) {
            // CameraX Viewfinder
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageCapture
                            )
                        } catch (_: Exception) {}
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.receipt_camera_title),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.receipt_camera_body),
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text(stringResource(R.string.receipt_allow_camera))
                }
            }
        }

        // Top Controls: Back button & Title
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 8.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.6f),
                modifier = Modifier.clickable { onNavigateBack() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.back), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.6f)
            ) {
                Text(
                    text = stringResource(R.string.receipt_title),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }

        // Framing Guide Box
        Box(
            modifier = Modifier
                .size(width = 280.dp, height = 380.dp)
                .align(Alignment.Center)
                .border(2.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                .background(Color.White.copy(alpha = 0.05f)),
            contentAlignment = Alignment.BottomCenter
        ) {
            Text(
                text = stringResource(R.string.align_frame_guide),
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        // Bottom Shutter & Triggers
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp, start = 24.dp, end = 24.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Gallery Picker
            IconButton(
                onClick = {
                    FinancesApp.instance.appLock.expectExternalActivity()
                    galleryLauncher.launch("image/*")
                },
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(Icons.Default.Image, contentDescription = stringResource(R.string.receipt_pick_image), tint = Color.White)
            }

            // Capture Button
            Surface(
                modifier = Modifier
                    .size(76.dp)
                    .clickable(enabled = !state.isProcessing) {
                        // Keep the capture rotation in step with the display (rotation / multi-window).
                        view.display?.rotation?.let { imageCapture.targetRotation = it }
                        imageCapture.takePicture(
                            captureExecutor,
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                                    // Runs on the capture executor, not the main thread. The JPEG is not
                                    // pre-rotated; rotationDegrees is applied once, by ML Kit.
                                    val bytes: ByteArray
                                    val rotation: Int
                                    try {
                                        val buffer = imageProxy.planes[0].buffer
                                        bytes = ByteArray(buffer.remaining())
                                        buffer.get(bytes)
                                        rotation = imageProxy.imageInfo.rotationDegrees
                                    } catch (_: Exception) {
                                        viewModel.reportCaptureFailed()
                                        return
                                    } finally {
                                        imageProxy.close()
                                    }
                                    viewModel.processCapture(bytes, rotation)
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    viewModel.reportCaptureFailed()
                                }
                            }
                        )
                    },
                shape = CircleShape,
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(4.dp, MaterialTheme.colorScheme.primary)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Camera, contentDescription = stringResource(R.string.receipt_take_photo), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                }
            }

            // Demo Synthetic Receipt Trigger
            IconButton(
                onClick = {
                    viewModel.processDemoText(
                        "SPAR MAGYARORSZAG KFT.\n2026.03.28 10:15\nTEJ 399 Ft\nKENYER 850 Ft\nFIZETENDO: 1 249 Ft\nKESZPENZ: 2 000 Ft\nVISSZAJARO: 751 Ft\nAFA TARTALOM: 60 Ft"
                    )
                },
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = stringResource(R.string.receipt_try_sample), tint = MaterialTheme.finances.success)
            }
        }

        // Errors slide in under the title bar and stay until dismissed, retried or replaced. The last
        // message is kept while the banner animates out so the text does not vanish first.
        var lastError by remember { mutableStateOf(R.string.receipt_error_ocr) }
        state.errorMessage?.let { lastError = it }
        AnimatedVisibility(
            visible = state.errorMessage != null,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = Motion.appear(),
            exit = Motion.disappear()
        ) {
            Surface(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(top = 64.dp, start = 24.dp, end = 24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { viewModel.dismissError() }
                    .semantics { liveRegion = LiveRegionMode.Assertive },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Text(stringResource(lastError), modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }

        // OCR progress overlay: fades in over the viewfinder without moving any control.
        AnimatedVisibility(
            visible = state.isProcessing,
            enter = Motion.fadeInShort(),
            exit = Motion.fadeOutShort()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f))
                    .semantics { liveRegion = LiveRegionMode.Polite },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.processing_ocr),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Receipt Review Bottom Sheet
        if (state.scannedReceipt != null) {
            val scan = state.scannedReceipt!!
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

            ModalBottomSheet(
                onDismissRequest = { viewModel.clearScan() },
                sheetState = sheetState
            ) {
                var merchant: String by remember { mutableStateOf(scan.merchant.orEmpty()) }
                // No total recognized: leave the field empty instead of inventing a value.
                val initialAmount = scan.totalMinor?.let { Money.toMajor(abs(it)).stripTrailingZeros().toPlainString() }.orEmpty()
                var totalAmountText: String by remember { mutableStateOf(initialAmount) }
                var selectedCurrency by remember { mutableStateOf(scan.currency.ifBlank { "HUF" }) }
                val parsedTotal = Money.parseUserInput(totalAmountText)?.takeIf { it > 0 }

                val merchantLabel = stringResource(R.string.merchant)
                val totalLabel = "${stringResource(R.string.total)} ($selectedCurrency)"

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = stringResource(R.string.review_receipt),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (state.isSample) {
                        Text(
                            stringResource(R.string.receipt_sample_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    // A failed save is reported here, inside the sheet, where the user is looking.
                    AnimatedVisibility(
                        visible = state.errorMessage != null,
                        enter = Motion.appear(),
                        exit = Motion.disappear()
                    ) {
                        Text(
                            stringResource(state.errorMessage ?: lastError),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }
                        )
                    }
                    Text(
                        stringResource(R.string.receipt_check_values),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = merchant,
                        onValueChange = { merchant = it },
                        label = { Text(merchantLabel) },
                        placeholder = { Text(stringResource(R.string.receipt_not_recognized)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = totalAmountText,
                        onValueChange = { totalAmountText = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(15) },
                        label = { Text(totalLabel) },
                        isError = totalAmountText.isNotBlank() && parsedTotal == null,
                        supportingText = { if (scan.totalMinor == null) Text(stringResource(R.string.receipt_total_missing)) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("HUF", "EUR", "USD", "GBP", "CHF").forEach { curr ->
                            androidx.compose.material3.FilterChip(
                                selected = selectedCurrency == curr,
                                onClick = { selectedCurrency = curr },
                                label = { Text(curr) }
                            )
                        }
                    }

                    val cashText = stringResource(R.string.payment_cash)
                    val cardText = stringResource(R.string.payment_card)
                    val methodLabel = stringResource(R.string.payment_method)
                    val methodVal = if (scan.paymentMethod == com.teppe21.finances.domain.model.PaymentMethod.CASH) cashText else cardText

                    Text(
                        text = "$methodLabel: $methodVal · Date: ${com.teppe21.finances.core.utils.DateFormatter.format(scan.date ?: LocalDate.now())}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(stringResource(R.string.account), style = MaterialTheme.typography.labelMedium)
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(state.accounts.size) { i ->
                            val acc = state.accounts[i]
                            androidx.compose.material3.FilterChip(
                                selected = state.selectedAccountId == acc.id,
                                onClick = { viewModel.setSelectedAccount(acc.id) },
                                label = { Text(accountLabel(acc)) }
                            )
                        }
                    }

                    Button(
                        onClick = {
                            val minor = parsedTotal ?: return@Button
                            val accId = state.selectedAccountId ?: return@Button
                            val catId = state.selectedCategoryId ?: "food"
                            viewModel.confirmSave(
                                merchant = merchant,
                                amountMinor = minor,
                                date = scan.date ?: LocalDate.now(),
                                accountId = accId,
                                categoryId = catId,
                                currency = selectedCurrency
                            ) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onNavigateBack()
                            }
                        },
                        enabled = parsedTotal != null && state.selectedAccountId != null && merchant.isNotBlank() && !state.isSaving,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.confirm_save))
                    }
                }
            }
        }
    }
}

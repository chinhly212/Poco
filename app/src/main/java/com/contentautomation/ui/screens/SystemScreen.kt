package com.contentautomation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.contentautomation.ui.MainViewModel
import com.contentautomation.ui.components.*
import com.contentautomation.ui.models.PipelineState
import com.contentautomation.ui.models.PipelineSteps

@Composable
fun SystemScreen(vm: MainViewModel, state: PipelineState) {
    ScreenColumn(title = "Hệ thống", subtitle = "Tiến trình xử lý video tự động") {
        if (state is PipelineState.Idle) {
            GlassCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(GlassColors.Red)
                    Spacer(Modifier.width(12.dp))
                    GText("Không phát hiện video mới", 15.sp, color = GlassColors.InkSoft)
                }
            }
        } else if (state is PipelineState.Running) {
            val currentStep = state.stepIndex
            val progress = (currentStep + 1) / PipelineSteps.all.size.toFloat()

            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(GlassColors.Green)
                        Spacer(Modifier.width(12.dp))
                        GText("Đang xử lý video mới!", 15.sp, color = GlassColors.Green, weight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = GlassColors.Blue,
                        trackColor = GlassColors.InkFaint
                    )
                    Spacer(Modifier.height(8.dp))
                    GText("Bước ${currentStep + 1}/${PipelineSteps.all.size}", 13.sp, color = GlassColors.InkSoft)
                    if (state.retry > 0) {
                        GText("Đang thử lại lần ${state.retry}...", 12.sp, color = GlassColors.Amber)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            GText("Chi tiết các bước", 16.sp, weight = FontWeight.SemiBold)
            
            PipelineSteps.all.forEachIndexed { index, step ->
                val isDone = index < currentStep
                val isRunning = index == currentStep
                GlassCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        val dotColor = when {
                            isDone -> GlassColors.Green
                            isRunning -> GlassColors.Blue
                            else -> GlassColors.InkFaint
                        }
                        StatusDot(dotColor)
                        Spacer(Modifier.width(12.dp))
                        GText(
                            text = when {
                                isDone -> step.done
                                isRunning -> step.running
                                else -> step.pending
                            },
                            size = 14.sp,
                            color = if (isRunning) GlassColors.Blue else if (isDone) GlassColors.Ink else GlassColors.InkSoft,
                            weight = if (isRunning) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

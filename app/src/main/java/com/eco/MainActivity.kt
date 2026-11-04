package com.eco

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.constant.area.DoorValue

class MainActivity : AppCompatActivity() {
    private var vehicle: Vehicle? = null
    private lateinit var resultView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        resultView = TextView(this).apply {
            text = "도어 위치 조회 중…"
            textSize = 20f
            setPadding(32, 32, 32, 32)
        }
        setContentView(resultView)

        vehicle = Vehicle(this).also { it.initialize() }
        vehicle?.getDoor()?.getCurrentDoorPosition(
            area = DoorArea(DoorValue.ROW_1_LEFT),
            onComplete = { position ->
                runOnUiThread { resultView.text = "운전석 도어 위치: $position" }
            },
            onFailed = { error ->
                runOnUiThread { resultView.text = "도어 위치 조회 실패: ${error.message}" }
            }
        )
    }

    override fun onDestroy() {
        vehicle?.release()
        vehicle = null
        super.onDestroy()
    }
}

package com.mondiro.monkey

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText

/** Màn hình riêng để sửa danh sách viết tắt khi nói (danh sách sẽ ngày càng dài). */
class AbbrevActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_abbrev)
        val prefs = Prefs(this)
        val box = findViewById<EditText>(R.id.editAbbrev)
        box.setText(prefs.voiceAbbrevList)
        box.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { prefs.voiceAbbrevList = s?.toString().orEmpty() }
        })
        findViewById<android.view.View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnAbbrevDefault).setOnClickListener {
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Khôi phục danh sách mặc định?")
                .setMessage("Các từ bạn tự thêm sẽ bị xoá.")
                .setPositiveButton("Khôi phục") { _, _ -> box.setText(Abbrev.DEFAULT) }
                .setNegativeButton("Huỷ", null)
                .show()
        }
    }
}

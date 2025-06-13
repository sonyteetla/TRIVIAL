// MainActivity.kt
package com.example.trivial

import android.Manifest
import android.os.Bundle
import android.telephony.SmsManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.trivial.ui.theme.TrivialTheme
import java.util.*
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import android.util.Base64

class MainActivity : ComponentActivity() {

    private lateinit var aesKey: SecretKey
    private val sdesKey = "1010000010" // Fixed 10-bit key

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        aesKey = generateAESKey()

        // ✅ Register the permission launcher
        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (!isGranted) {
                Toast.makeText(this, "SMS permission denied", Toast.LENGTH_LONG).show()
            }
        }

        // ✅ Request SEND_SMS permission
        requestPermissionLauncher.launch(Manifest.permission.SEND_SMS)

        setContent {
            TrivialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    EncryptDecryptScreen(aesKey, sdesKey)
                }
            }
        }
    }


private fun generateAESKey(): SecretKey {
    val keyGen = KeyGenerator.getInstance("AES")
    keyGen.init(128)
    return keyGen.generateKey()
}
}

@Composable
fun EncryptDecryptScreen(aesKey: SecretKey, sdesKey: String) {
    val context = LocalContext.current

    var phoneNumber by remember { mutableStateOf(TextFieldValue("")) }
    var message by remember { mutableStateOf(TextFieldValue("")) }
    var encryptedMessage by remember { mutableStateOf("") }
    var decryptedMessage by remember { mutableStateOf("") }
    var selectedAlgo by remember { mutableStateOf("AES") }

    val algorithms = listOf("AES", "S-DES")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("🔐 NMIT Secure Message Sender", style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(
            value = phoneNumber,
            onValueChange = { phoneNumber = it },
            label = { Text("Enter Phone Number") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = message,
            onValueChange = { message = it },
            label = { Text("Enter Message") },
            modifier = Modifier.fillMaxWidth()
        )

        Text("Choose Encryption Technique:")
        algorithms.forEach { algo ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = (selectedAlgo == algo),
                    onClick = { selectedAlgo = algo }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(algo)
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    encryptedMessage = when (selectedAlgo) {
                        "AES" -> encryptAES(message.text, aesKey)
                        "S-DES" -> sdesEncrypt(message.text, sdesKey)
                        else -> ""
                    }

                    SmsManager.getDefault().sendTextMessage(
                        phoneNumber.text, null, encryptedMessage, null, null
                    )
                    Toast.makeText(context, "Encrypted & Sent!", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
            ) {
                Text("Encrypt & Send", color = Color.White)
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    decryptedMessage = when (selectedAlgo) {
                        "AES" -> decryptAES(encryptedMessage, aesKey)
                        "S-DES" -> sdesDecrypt(encryptedMessage, sdesKey)
                        else -> ""
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
            ) {
                Text("Decrypt", color = Color.White)
            }
        }

        if (encryptedMessage.isNotEmpty()) {
            Text("🔒 Encrypted Message:\n$encryptedMessage")
        }

        if (decryptedMessage.isNotEmpty()) {
            Text("🔓 Decrypted Message:\n$decryptedMessage", color = Color(0xFF00796B))
        }
    }
}


fun encryptAES(data: String, key: SecretKey): String {
    val cipher = Cipher.getInstance("AES")
    cipher.init(Cipher.ENCRYPT_MODE, key)
    val encrypted = cipher.doFinal(data.toByteArray())
    return Base64.encodeToString(encrypted, Base64.DEFAULT)
}

fun decryptAES(encryptedData: String, key: SecretKey): String {
    val cipher = Cipher.getInstance("AES")
    cipher.init(Cipher.DECRYPT_MODE, key)
    val decoded = Base64.decode(encryptedData, Base64.DEFAULT)
    return String(cipher.doFinal(decoded))
}
fun generateKeys(key: String): Pair<String, String> {
    val P10 = listOf(3, 5, 2, 7, 4, 10, 1, 9, 8, 6)
    val P8 = listOf(6, 3, 7, 4, 8, 5, 10, 9)
    val permuted = permute(key, P10)
    var left = permuted.substring(0, 5)
    var right = permuted.substring(5)
    left = leftShift(left, 1)
    right = leftShift(right, 1)
    val K1 = permute(left + right, P8)
    left = leftShift(left, 2)
    right = leftShift(right, 2)
    val K2 = permute(left + right, P8)
    return Pair(K1, K2)
}
fun permute(bits: String, order: List<Int>): String {
    return order.map { bits[it - 1] }.joinToString("")
}
fun leftShift(bits: String, n: Int): String {
    return bits.drop(n) + bits.take(n)
}
fun sboxLookup(bits: String, sbox: Array<IntArray>): String {
    val row = Integer.parseInt("${bits[0]}${bits[3]}", 2)
    val col = Integer.parseInt("${bits[1]}${bits[2]}", 2)
    return Integer.toBinaryString(sbox[row][col]).padStart(2, '0')
}
fun fk(bits: String, key: String): String {
    val EP = listOf(4, 1, 2, 3, 2, 3, 4, 1)
    val P4 = listOf(2, 4, 3, 1)
    val S0 = arrayOf(
        intArrayOf(1, 0, 3, 2),
        intArrayOf(3, 2, 1, 0),
        intArrayOf(0, 2, 1, 3),
        intArrayOf(3, 1, 3, 2)
    )
    val S1 = arrayOf(
        intArrayOf(0, 1, 2, 3),
        intArrayOf(2, 0, 1, 3),
        intArrayOf(3, 0, 1, 0),
        intArrayOf(2, 1, 0, 3)
    )
    val left = bits.substring(0, 4)
    val right = bits.substring(4)
    val rightExpanded = permute(right, EP)
    val xorResult = Integer.toBinaryString(
        Integer.parseInt(rightExpanded, 2) xor Integer.parseInt(key, 2)
    ).padStart(8, '0')
    val s0Bits = sboxLookup(xorResult.substring(0, 4), S0)
    val s1Bits = sboxLookup(xorResult.substring(4), S1)
    val p4Result = permute(s0Bits + s1Bits, P4)
    val leftXor = Integer.toBinaryString(
        Integer.parseInt(left, 2) xor Integer.parseInt(p4Result, 2)
    ).padStart(4, '0')
    return leftXor + right
}
fun encryptSDES(plain: String, key: String): String {
    val IP = listOf(2, 6, 3, 1, 4, 8, 5, 7)
    val IPinv = listOf(4, 1, 3, 5, 7, 2, 8, 6)
    val (K1, K2) = generateKeys(key)
    var bits = permute(plain, IP)
    bits = fk(bits, K1)
    bits = bits.substring(4) + bits.substring(0, 4)
    bits = fk(bits, K2)
    return permute(bits, IPinv)
}
fun decryptSDES(cipher: String, key: String): String {
    val IP = listOf(2, 6, 3, 1, 4, 8, 5, 7)
    val IPinv = listOf(4, 1, 3, 5, 7, 2, 8, 6)
    val (K1, K2) = generateKeys(key)
    var bits = permute(cipher, IP)
    bits = fk(bits, K2)
    bits = bits.substring(4) + bits.substring(0, 4)
    bits = fk(bits, K1)
    return permute(bits, IPinv)
}
fun sdesEncrypt(plainText: String, key10: String): String {
    val binary = plainText.map { it.code.toString(2).padStart(8, '0') }.joinToString("")
    val encryptedBinary = binary.chunked(8).joinToString("") { encryptSDES(it, key10) }
    return Base64.encodeToString(
        encryptedBinary.chunked(8).map { it.toInt(2).toByte() }.toByteArray(),
        Base64.DEFAULT
    )
}
fun sdesDecrypt(encryptedBase64: String, key10: String): String {
    val decodedBytes = Base64.decode(encryptedBase64, Base64.DEFAULT)
    val binary = decodedBytes.joinToString("") { it.toInt().and(0xFF).toString(2).padStart(8, '0') }
    val decryptedBinary = binary.chunked(8).joinToString("") { decryptSDES(it, key10) }
    return decryptedBinary.chunked(8).map { it.toInt(2).toChar() }.joinToString("")
}


package com.ZEN.zensinbox

import android.Manifest
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Bundle
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ZEN.zensinbox.databinding.FragmentSecondBinding
import com.ZEN.zensinbox.databinding.ItemMessageBinding
import java.text.SimpleDateFormat
import java.util.*

data class Message(
    val id: String,
    val body: String,
    val date: Long,
    val type: Int
)

class SecondFragment : Fragment() {

    private var _binding: FragmentSecondBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: MessageAdapter
    private var threadId: String? = null
    private var phoneNumber: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSecondBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        threadId = arguments?.getString("threadId")
        phoneNumber = arguments?.getString("address")

        setupRecyclerView()
        setupSendButton()
        loadMessages()
    }

    private fun setupRecyclerView() {
        adapter = MessageAdapter()
        binding.recyclerViewMessages.apply {
            layoutManager = LinearLayoutManager(context).apply {
                stackFromEnd = true
            }
            adapter = this@SecondFragment.adapter
        }
    }

    private fun setupSendButton() {
        binding.buttonSend.setOnClickListener {
            sendMessage()
        }
    }

    private fun loadMessages() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(context, "SMS permission required", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val messages = mutableListOf<Message>()
            val uri = Telephony.Sms.CONTENT_URI
            val selection = "${Telephony.Sms.THREAD_ID} = ?"
            val selectionArgs = arrayOf(threadId)

            val cursor: Cursor? = requireContext().contentResolver.query(
                uri,
                arrayOf(
                    Telephony.Sms._ID,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                    Telephony.Sms.TYPE
                ),
                selection,
                selectionArgs,
                "${Telephony.Sms.DATE} ASC"
            )

            cursor?.use {
                while (it.moveToNext()) {
                    messages.add(
                        Message(
                            id = it.getString(it.getColumnIndexOrThrow(Telephony.Sms._ID)),
                            body = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.BODY)),
                            date = it.getLong(it.getColumnIndexOrThrow(Telephony.Sms.DATE)),
                            type = it.getInt(it.getColumnIndexOrThrow(Telephony.Sms.TYPE))
                        )
                    )
                }
            }

            adapter.submitList(messages)
            if (messages.isNotEmpty()) {
                binding.recyclerViewMessages.scrollToPosition(messages.size - 1)
            }
        } catch (e: Exception) {
            Log.e("SecondFragment", "Error loading messages", e)
        }
    }

    private fun sendMessage() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.SEND_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(context, "Send SMS permission required", Toast.LENGTH_SHORT).show()
            return
        }

        val messageText = binding.editTextMessage.text.toString().trim()
        if (messageText.isNotEmpty() && phoneNumber != null) {
            try {
                val smsManager = SmsManager.getDefault()
                smsManager.sendTextMessage(phoneNumber, null, messageText, null, null)
                binding.editTextMessage.text.clear()
                
                binding.recyclerViewMessages.postDelayed({
                    loadMessages()
                }, 300)
            } catch (e: Exception) {
                Log.e("SecondFragment", "Error sending message", e)
                Toast.makeText(context, "Failed to send", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.title = phoneNumber ?: "Messages"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class MessageAdapter : RecyclerView.Adapter<MessageAdapter.ViewHolder>() {

    private var messages = listOf<Message>()

    class ViewHolder(val binding: ItemMessageBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMessageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val message = messages[position]
        val isSent = message.type == Telephony.Sms.MESSAGE_TYPE_SENT
        
        holder.binding.apply {
            textViewMessage.text = message.body
            textViewTime.text = formatDate(message.date)
            
            // Set alignment and style
            val containerParams = messageContainer.layoutParams as FrameLayout.LayoutParams
            if (isSent) {
                containerParams.gravity = Gravity.END
                textViewMessage.setBackgroundResource(R.drawable.message_bubble_sent)
                textViewMessage.setTextColor(holder.itemView.context.getColor(R.color.text_sent))
                textViewTime.gravity = Gravity.END
            } else {
                containerParams.gravity = Gravity.START
                textViewMessage.setBackgroundResource(R.drawable.message_bubble_received)
                textViewMessage.setTextColor(holder.itemView.context.getColor(R.color.text_received))
                textViewTime.gravity = Gravity.START
            }
            messageContainer.layoutParams = containerParams
        }
    }

    override fun getItemCount() = messages.size

    fun submitList(newMessages: List<Message>) {
        messages = newMessages
        notifyDataSetChanged()
    }

    private fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}
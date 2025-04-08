// const socket = new WebSocket("ws://localhost:8080/ws/chat");
const socket = new WebSocket("wss://46.202.178.139:9999/ws/chat");

const joinRoomBtn = document.getElementById('joinRoomBtn');
const roomIdInput = document.getElementById('roomIdInput');
const nameInput = document.getElementById('nameInput');
const createRoomBtn = document.getElementById('createRoomBtn');
const roomDisplay = document.getElementById('roomDisplay');
const chatBox = document.getElementById("chatBox");
const sendBtn = document.getElementById("sendBtn");
const messageInput = document.getElementById("messageInput");
const roomTypeSelect = document.getElementById("roomTypeSelect")

let roomId = null;

// Tạo phòng mới
createRoomBtn.onclick = () => {
    const name = nameInput.value.trim();
    const roomType = roomTypeSelect.value.trim();
    if (!name || !roomType) return alert("ban chua nhap day du")
    socket.send(JSON.stringify({ type: 'create-room', name: name, roomType: roomType }));
};

// Tham gia phòng
joinRoomBtn.onclick = () => {
    const id = roomIdInput.value.trim();
    const name = nameInput.value.trim();
    if (!name) return  alert("ban chua nhap ten")
    if (id) {
        roomId = id;
        socket.send(JSON.stringify({ type: 'join', roomId: id , name: name}));
        roomDisplay.innerText = `Room ID: ${roomId}`;
    } else {
        alert('Please enter a room ID');
    }
};

// Gửi tin nhắn
sendBtn.onclick = () => {
    const message = messageInput.value.trim();
    if (message && roomId) {
        socket.send(JSON.stringify({ type: 'chat', roomId, message }));
        messageInput.value = "";
    }
};

// Nhận dữ liệu từ server
socket.onmessage = (event) => {
    const { action, data, from } = JSON.parse(event.data);

    switch (action) {
        case 'error':
            alert(data);
            break;
        case 'room-created':
            roomId = data;
            roomDisplay.innerText = `Room ID: ${roomId}`;
            break;
        case 'chat':
            appendMessage(from === socket.id ? "Me" : from, data);
            break;
        case 'new-user':
            appendMessage("System", `User ${data} joined the room.`);
            break;
        case 'user-disconnected':
            appendMessage("System", `User ${data} left the room.`);
            break;
        default:
            console.warn("Unknown action:", action);
    }
};

// Hiển thị tin nhắn
function appendMessage(sender, message) {
    const msgEl = document.createElement("div");
    msgEl.innerHTML = `<strong>${sender}:</strong> ${message}`;
    chatBox.appendChild(msgEl);
    chatBox.scrollTop = chatBox.scrollHeight;
}

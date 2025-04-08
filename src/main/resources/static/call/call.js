// const socket = new WebSocket("ws://localhost:8080/ws/call");
const socket = new WebSocket("wss://46.202.178.139:9999/ws/call");
const videosDiv = document.getElementById('videos');
const localVideoElement = document.getElementById('localVideo');
const joinRoomBtn = document.getElementById('joinRoomBtn');
const roomIdInput = document.getElementById('roomIdInput');
const createRoomBtn = document.getElementById('createRoomBtn');
const roomDisplay = document.getElementById('roomDisplay');
const roomTypeSelect = document.getElementById("roomTypeSelect")
const nameInput = document.getElementById('nameInput');

let localStream;
let peerConnections = {};
let roomId;


async function getLocalStream() {
    localStream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true });
    localVideoElement.srcObject = localStream;
}

createRoomBtn.onclick = () => {
    const name = nameInput.value.trim();
    const roomType = roomTypeSelect.value.trim();
    if (!name || !roomType) return  alert("ban chua nhap day du")

    socket.send(JSON.stringify({ type: 'create-room', name: name, roomType: roomType }));
};


joinRoomBtn.onclick = () => {
    roomId = roomIdInput.value.trim();
    const name = nameInput.value.trim();

    if (!name) return alert("ban chua nhap name")

    if (roomId) {
        socket.send(JSON.stringify({ type: 'join', roomId, name: name }));
    } else {
        alert('Please enter a room ID');
    }
};

socket.onmessage = async (event) => {
    const message = JSON.parse(event.data);

    const { action, data , from, username } = message;
    
    switch (action) {
        case 'error':
            alert(data)
            break;
        case 'room-created':
            roomId = data;
            roomDisplay.innerText = `Room ID: ${roomId}`;
            break;
        case 'new-user':
            createPeerConnection(from, username);
            sendOffer(from);
            break;
        case 'offer':
            handleOffer(data, from, username);
            break;
        case 'answer':
            handleAnswer(data, from);
            break;
        case 'ice-candidate':
            handleIceCandidate(data, from);
            break;
        case 'user-disconnected':
            removeUser(from);
            break;
        default:
            console.error('Unknown message type:', message);
    }
};

function createPeerConnection(userId, username) {
    const peerConnection = new RTCPeerConnection();

    localStream.getTracks().forEach(track => {
        peerConnection.addTrack(track, localStream);
    });

    peerConnection.onicecandidate = (event) => {
        if (event.candidate) {
            socket.send(JSON.stringify({ type: 'ice-candidate', data: event.candidate, to: userId, username: username }));
        }
    };
    peerConnection.ontrack = (event) => {
        if (event.streams.length > 0) {
            let remoteStream = event.streams[0];
            let existingVideo = videosDiv.querySelector(`div[data-user-id="${userId}"]`);
            if (!existingVideo) {
                // Tạo container cho video và tên
                const container = document.createElement('div');
                container.dataset.userId = userId;
                container.style.display = 'inline-block';
                container.style.margin = '10px';
                container.style.textAlign = 'center';

                const remoteVideo = document.createElement('video');
                remoteVideo.srcObject = remoteStream;
                remoteVideo.autoplay = true;
                remoteVideo.style.width = "200px";
                remoteVideo.style.borderRadius = "8px";

                const nameLabel = document.createElement('div');
                nameLabel.textContent = username || "Unknown";
                nameLabel.style.marginTop = "5px";
                nameLabel.style.fontWeight = "bold";
                nameLabel.style.color = "#333";

                container.appendChild(remoteVideo);
                container.appendChild(nameLabel);
                videosDiv.appendChild(container);
            }
        }
    };


    peerConnections[userId] = peerConnection;
    return peerConnection;
}

async function sendOffer(userId) {
    const peerConnection = peerConnections[userId];
    const offer = await peerConnection.createOffer();
    await peerConnection.setLocalDescription(offer);
    socket.send(JSON.stringify({ type: 'offer', data: offer, to: userId }));
}

async function handleOffer(offer, from, username) {
    let peerConnection = peerConnections[from] || createPeerConnection(from, username);
    await peerConnection.setRemoteDescription(new RTCSessionDescription(offer));
    const answer = await peerConnection.createAnswer();
    await peerConnection.setLocalDescription(answer);
    socket.send(JSON.stringify({ type: 'answer', data: answer, to: from }));
}

async function handleAnswer(answer, from) {
    const peerConnection = peerConnections[from];
    await peerConnection.setRemoteDescription(new RTCSessionDescription(answer));
}

function handleIceCandidate(candidate, from) {
    const peerConnection = peerConnections[from];
    peerConnection.addIceCandidate(new RTCIceCandidate(candidate));
}

function removeUser(userId) {
    if (peerConnections[userId]) {
        peerConnections[userId].close();
        delete peerConnections[userId];
    }
    const videoElement = videosDiv.querySelector(`video[data-user-id="${userId}"]`);
    if (videoElement) {
        videoElement.remove();
    }
}

getLocalStream();

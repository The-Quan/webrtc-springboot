const socket = new WebSocket("wss://46.202.178.139:8080/ws");
const videosDiv = document.getElementById('videos');
const localVideoElement = document.getElementById('localVideo');
const joinRoomBtn = document.getElementById('joinRoomBtn');
const roomIdInput = document.getElementById('roomIdInput');

let localStream;
let peerConnections = {};
let roomId;

async function getLocalStream() {
    localStream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true });
    localVideoElement.srcObject = localStream;
}

joinRoomBtn.onclick = () => {
    roomId = roomIdInput.value.trim();
    if (roomId) {
        socket.send(JSON.stringify({ type: 'join', roomId }));
    } else {
        alert('Please enter a room ID');
    }
};

socket.onmessage = async (event) => {
    const message = JSON.parse(event.data);

    const { action, data , from } = message;

    console.log(action);


    switch (action) {
        case 'new-user':
            createPeerConnection(from);
            sendOffer(from);
            break;
        case 'offer':
            handleOffer(data, from);
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

function createPeerConnection(userId) {
    const peerConnection = new RTCPeerConnection();

    localStream.getTracks().forEach(track => {
        peerConnection.addTrack(track, localStream);
    });

    peerConnection.onicecandidate = (event) => {
        if (event.candidate) {
            socket.send(JSON.stringify({ type: 'ice-candidate', data: event.candidate, to: userId }));
        }
    };

    peerConnection.ontrack = (event) => {
        if (event.streams.length > 0) {
            let remoteStream = event.streams[0];
            let existingVideo = videosDiv.querySelector(`video[data-user-id="${userId}"]`);
            if (!existingVideo) {
                const remoteVideo = document.createElement('video');
                remoteVideo.srcObject = remoteStream;
                remoteVideo.autoplay = true;
                remoteVideo.style.width = "200px";
                remoteVideo.style.margin = "5px";
                remoteVideo.dataset.userId = userId;
                videosDiv.appendChild(remoteVideo);
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

async function handleOffer(offer, from) {
    console.log("answer")
    let peerConnection = peerConnections[from] || createPeerConnection(from);
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

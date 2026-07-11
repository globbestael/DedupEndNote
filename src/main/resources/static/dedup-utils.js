var stompClient = null;
var dedupFinished = false;

function hideDiv(id) {
	$(id).removeClass('d-block');
	$(id).addClass('d-none');
}
function showDiv(id) {
	$(id).removeClass('d-none');
	$(id).addClass('d-block');
}
function showProgress(percentage) {
	$("#progress").html(`<div class="progress-bar" role="progressbar" style="width: ${percentage}%;" aria-valuenow="${percentage}" aria-valuemin="0" aria-valuemax="100">${percentage}%</div>`);
}
function disableButton(id) {
	$(id).prop("disabled", true);
	$('label[for="' + id.replace('#', '') + '"]').addClass('disabled');
}
function enableButton(id) {
	$(id).prop("disabled", false);
	$('label[for="' + id.replace('#', '') + '"]').removeClass('disabled');
}
function markAsDone(id) {
	$(id).removeClass('step-waiting step-active').addClass('step-done');
}
function markAsActive(id) {
	$(id).removeClass('step-waiting step-done').addClass('step-active');
}
function disconnect() {
	if (stompClient !== null) {
		stompClient.disconnect();
	}
	console.log("Disconnected");
}

function uploadWithXHR(formData, url, onSuccess, onError) {
	var xhr = new XMLHttpRequest();
	xhr.upload.addEventListener('progress', function(e) {
		if (e.lengthComputable) {
			showProgress(parseInt(e.loaded / e.total * 100, 10));
		}
	});
	xhr.addEventListener('load', function() {
		if (xhr.status >= 200 && xhr.status < 300) {
			onSuccess();
		} else {
			onError(xhr);
		}
	});
	xhr.addEventListener('error', function() { onError(xhr); });
	showProgress(0);
	xhr.open('POST', url);
	xhr.send(formData);
}

function connect() {
	var wssessionId = $("#wssessionId").val();
	var socket = new SockJS('/gs-guide-websocket');
	stompClient = Stomp.over(socket);
	stompClient.debug = null;
	stompClient.connect({}, function (frame) {
		stompClient.subscribe('/topic/messages-' + wssessionId, function (stompMessage) {
			if (dedupFinished) return;
			let message = JSON.parse(stompMessage.body).name
			if (message.match("^DONE")) {
				dedupFinished = true;
				showProgress(0);
				$('#results').text(message);
				enableButton('#buttonResultFile');
				markAsDone('#step2');
				markAsActive('#step3');
			} else if (message.match("^ERROR")) {
				dedupFinished = true;
				$('#results').removeClass('alert-warning');
				$('#results').addClass('alert-danger');
				$('#results').text(message);
			} else if (message.match("^PROGRESS:")) {
				var found = message.match(/^PROGRESS: (.+)$/);
				var percentage = found[1];
				showProgress(percentage);
			} else {
				console.log("DATA: " + message);
				$('#results').html($('<span>').text(message));
			}
		});
	});
}

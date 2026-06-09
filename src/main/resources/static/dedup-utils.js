var stompClient = null;

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

function connect() {
	var wssessionId = $("#wssessionId").val();
	var socket = new SockJS('/gs-guide-websocket');
	stompClient = Stomp.over(socket);
	stompClient.debug = null;
	stompClient.connect({}, function (frame) {
		stompClient.subscribe('/topic/messages-' + wssessionId, function (stompMessage) {
			let message = JSON.parse(stompMessage.body).name
			if (message.match("^DONE")) {
				showProgress(0);
				$('#results').text(message);
				enableButton('#buttonResultFile');
				markAsDone('#step2');
				markAsActive('#step3');
			} else if (message.match("^ERROR")) {
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

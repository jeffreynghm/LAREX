function ViewerInput(controller) {
	const _controller = controller;
	let _mouseSelecting = false;

	this.enterSection = function(sectionID, event) {
		_controller.enterSegment(sectionID, true);
	}

	this.leaveSection = function(sectionID, event) {
		_controller.leaveSegment(sectionID, false);
	}

	this.selectSection = function(sectionID, event, hitTest) {
		switch (event.event.button) {
			// leftclick
			case 0:
				_controller.selectSegment(sectionID, hitTest);
				break;
			// middleclick
			case 1:
				break;
			// rightclick
			case 2:
				if(!_controller.isSegmentSelected(sectionID)){
					_controller.unSelect();
					_controller.selectSegment(sectionID, hitTest);
					_controller.openContextMenu(true);
				}
				_controller.endCreateReadingOrder();
				break;
		}
	}

	this.dragImage = function(event){
		switch (event.event.button) {
			// leftclick
			case 0:
				if(event.modifiers.shift){
					_controller.startRectangleSelect();
				}else{
					_controller.moveImage(event.delta);
				}
				break;
			// middleclick
			case 1:
				break;
			// rightclick
			case 2:
				_controller.endCreateReadingOrder();
				break;
		}
	}

	this.dragBackground = function(event){
		switch (event.event.button) {
			// leftclick
			case 0:
				if(event.modifiers.shift){
					_controller.startRectangleSelect();
				}
				break;
			// middleclick
			case 1:
				break;
			// rightclick
			case 2:
				_controller.endCreateReadingOrder();
				break;
		}
	}

	this.clickImage = function(event, hitTest){
		switch (event.event.button) {
			// leftclick
			case 0:
				if(hitTest && hitTest.item && hitTest.segment){
					_controller.selectSegment(hitTest.item.id, point);
				}
				break;
			// middleclick
			case 1:
				break;
			// rightclick
			case 2:
				_controller.endCreateReadingOrder();
				break;
		}
		if(!event.modifiers.control){
			_controller.unSelect();
		}
		_controller.closeContextMenu();
	}

	this.clickBackground = function(event){
		switch (event.event.button) {
			// leftclick
			case 0:
				break;
			// middleclick
			case 1:
				break;
			// rightclick
			case 2:
				_controller.endCreateReadingOrder();
				break;
		}
		if(!event.modifiers.control){
			_controller.unSelect();
		}
		_controller.closeContextMenu();
	}
}

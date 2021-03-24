/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import {Component, OnInit, ViewChild, ElementRef} from '@angular/core';
import {ActivatedRoute, Router, NavigationStart} from '@angular/router';
import * as moment from 'moment/moment';
import {Subscription} from 'rxjs';

import {SearchService} from '../../service/search.service';
import {UpdateService} from '../../service/update.service';
import {Alert} from '../../model/alert';
import {AlertsService} from '../../service/alerts.service';
import {AlertSource} from '../../model/alert-source';
import {PatchRequest} from '../../model/patch-request';
import {Patch} from '../../model/patch';
import {AlertComment} from './alert-comment';
import {AuthenticationService} from '../../service/authentication.service';
import {CommentAddRemoveRequest} from '../../model/comment-add-remove-request';
import {META_ALERTS_SENSOR_TYPE} from '../../utils/constants';
import {GlobalConfigService} from '../../service/global-config.service';
import { DialogService } from 'app/service/dialog.service';
import { ConfirmationType } from 'app/model/confirmation-type';

export enum AlertState {
  NEW, OPEN, ESCALATE, DISMISS, RESOLVE
}
export enum Tabs {
  DETAILS, COMMENTS
}

export class AlertCommentWrapper {
  alertComment: AlertComment;
  displayTime: string;

  constructor(alertComment: AlertComment, displayTime: string) {
    this.alertComment = alertComment;
    this.displayTime = displayTime;
  }
}

@Component({
  selector: 'app-alert-details',
  templateUrl: './alert-details.component.html',
  styleUrls: ['./alert-details.component.scss']
})
export class AlertDetailsComponent implements OnInit {

  alertId = '';
  alertName = '';
  alertSourceType = '';
  showEditor = false;
  isMetaAlert = false;
  alertIndex = '';
  alertState = AlertState;
  tabs = Tabs;
  activeTab = Tabs.DETAILS;
  selectedAlertState: AlertState = AlertState.NEW;
  alertSource: AlertSource = new AlertSource();
  alertSources = [];
  alertFields: string[] = [];
  alertCommentStr = '';
  alertCommentsWrapper: AlertCommentWrapper[] = [];
  globalConfig: {} = {};
  globalConfigService: GlobalConfigService;
  configSubscription: Subscription;
  @ViewChild('metaAlertNameInput') metaAlertNameInput: ElementRef;

  constructor(private router: Router,
              private activatedRoute: ActivatedRoute,
              private searchService: SearchService,
              private updateService: UpdateService,
              private alertsService: AlertsService,
              private authenticationService: AuthenticationService,
              private dialogService: DialogService,
              globalConfigService: GlobalConfigService) {
    this.globalConfigService = globalConfigService;

    router.events.subscribe(event => {
      if (event instanceof NavigationStart && /\/alerts-list\(dialog:details\//.test(event.url)) {
        this.alertSources = [];
        this.alertSource = null;
      }
    });
  }

  goBack() {
    this.router.navigateByUrl('/alerts-list');
    return false;
  }

  getData() {
    this.alertCommentStr = '';
    this.searchService.getAlert(this.alertSourceType, this.alertId).subscribe(alertSource => {
      this.setAlert(alertSource);
    });
  }

  setAlert(alertSource) {
    this.alertName = alertSource.name;
    this.alertSource = alertSource;
    this.alertSources = (alertSource.metron_alert && alertSource.metron_alert.length > 0) ? alertSource.metron_alert : [alertSource];
    this.selectedAlertState = this.getAlertState(alertSource['alert_status']);
    let alertComments = alertSource['comments'] || [];
    this.alertCommentsWrapper = alertComments.map(alertComment =>
            new AlertCommentWrapper(alertComment, moment(new Date(alertComment.timestamp)).fromNow()));
  }

  getAlertState(alertStatus) {
    if (alertStatus === 'OPEN') {
      return AlertState.OPEN;
    } else if (alertStatus === 'ESCALATE') {
      return AlertState.ESCALATE;
    } else if (alertStatus === 'DISMISS') {
      return AlertState.DISMISS;
    } else if (alertStatus === 'RESOLVE') {
      return AlertState.RESOLVE;
    } else {
      return AlertState.NEW;
    }
  }

  ngOnInit() {
    this.configSubscription = this.globalConfigService.get().subscribe((config: {}) => {
      this.globalConfig = config;
    });

    this.activatedRoute.params.subscribe(params => {
      this.alertId = params['guid'];
      this.alertSourceType = params['source.type.field'];
      this.alertIndex = params['index'];
      this.isMetaAlert = this.alertSourceType === META_ALERTS_SENSOR_TYPE;
      this.getData();
    });
  };

  ngOnDestroy() {
    this.configSubscription.unsubscribe();
  }

  getScore(alertSource) {
    return alertSource[this.globalConfig['threat.triage.score.field']];
  }

  processOpen() {
    this.updateAlertState('OPEN');
  }

  processNew() {
    this.updateAlertState('NEW');
  }

  processEscalate() {
    this.updateAlertState('ESCALATE');

    let tAlert = new Alert();
    tAlert.source = this.alertSource;
    this.alertsService.escalate([tAlert]).subscribe();
  }

  processDismiss() {
    this.updateAlertState('DISMISS');
  }

  processResolve() {
    this.updateAlertState('RESOLVE');
  }

  updateAlertState(state: string) {
    let tAlert = new Alert();
    tAlert.source = this.alertSource;

    this.updateService.updateAlertState([tAlert], state).subscribe(alertSource => {
      this.setAlert(alertSource[0]);
    });
  }

  toggleNameEditor() {
    if (this.alertSources.length > 1) {
      this.showEditor = !this.showEditor;
    }
    setTimeout(() => {
      if (this.showEditor && this.metaAlertNameInput) {
        this.metaAlertNameInput.nativeElement.focus();
      }
    }, 100);
  }

  saveName() {
    if (!this.isSaveNameButtonDisabled()) {
      let patchRequest = new PatchRequest();
      patchRequest.guid = this.alertId;
      patchRequest.sensorType = 'metaalert';
      patchRequest.patch = [new Patch('add', '/name', this.alertName)];

      this.updateService.patch(patchRequest).subscribe(alertSource => {
        this.setAlert(alertSource);
        this.toggleNameEditor();
      }, () => {
        this.toggleNameEditor();
      });
    }
  }

  onAddComment() {
    let commentRequest = new CommentAddRemoveRequest();
    commentRequest.guid = this.alertSource.guid;
    commentRequest.comment = this.alertCommentStr;
    commentRequest.username = this.authenticationService.getCurrentUserName();
    commentRequest.timestamp = new Date().getTime();
    commentRequest.sensorType = this.alertSourceType;
    this.updateService.addComment(commentRequest).subscribe(alertSource => {
      this.setAlert(alertSource);
    });
  }

  onDeleteComment(index: number) {
    let commentText =  'Do you wish to delete the comment ';
    if (this.alertCommentsWrapper[index].alertComment.comment.length > 25 ) {
      commentText += ' \'' + this.alertCommentsWrapper[index].alertComment.comment.substr(0, 25) + '...\'';
    } else {
      commentText += ' \'' + this.alertCommentsWrapper[index].alertComment.comment + '\'';
    }

    const confirmedSubscription = this.dialogService.launchDialog(commentText).subscribe(action => {
      if (action === ConfirmationType.Confirmed) {
        let commentRequest = new CommentAddRemoveRequest();
        commentRequest.guid = this.alertSource.guid;
        commentRequest.comment = this.alertCommentsWrapper[index].alertComment.comment;
        commentRequest.username = this.alertCommentsWrapper[index].alertComment.username;
        commentRequest.timestamp = this.alertCommentsWrapper[index].alertComment.timestamp;
        commentRequest.sensorType = this.alertSourceType;
        this.updateService.removeComment(commentRequest).subscribe(alertSource => {
          this.setAlert(alertSource);
        });
      }
      confirmedSubscription.unsubscribe();
    });
  }

  isSaveNameButtonDisabled() {
    return !this.alertName || this.alertName.length === 0;
  }

  onSaveNameInputKeyPress(e: KeyboardEvent) {
    if (e.code === 'Enter') {
      this.saveName();
    } else if (e.code === 'Escape') {
      this.alertName = this.alertSource.name;
      this.toggleNameEditor();
    }
  }

  onSaveNameInputCancel() {
    this.alertName = this.alertSource.name;
    this.toggleNameEditor();
  }
}

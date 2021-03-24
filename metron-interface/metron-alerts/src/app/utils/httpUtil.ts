
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
import {HttpErrorResponse, HttpResponse} from '@angular/common/http';
import {RestError} from '../model/rest-error';
import {throwError, Observable} from 'rxjs';
import {AppConfigService} from '../service/app-config.service';

export class HttpUtil {

  public static extractString(res: HttpErrorResponse): string {
    let text: string = res.toString();
    return text || '';
  }

  public static extractData(res: HttpResponse<any>): any {
    let body = res;
    return body || {};
  }

  /**
   * @deprecated Turning all errors to 404 and hiding actual errors from the consumers
   *             could limit how we can recover or react to errors.
   *             Use sessionExpiration instead and/or introduce new composable error handlers.
   */
  public static handleError(res: HttpErrorResponse): Observable<RestError> {
    let restError: RestError;
    if (res.status === 401) {
      HttpUtil.navigateToLogin();
    } else if (res.status !== 404) {
      restError = res;
    } else {
      restError = new RestError();
      restError.status = 404;
    }
    return throwError(restError);
  }

  public static sessionExpiration(res: HttpErrorResponse): Observable<RestError> {
    if (res.status === 401) {
      HttpUtil.navigateToLogin();
    }
    return throwError(res);
  }

  public static navigateToLogin() {
    let loginPath = AppConfigService.getAppConfigStatic()['loginPath'];
    location.href = loginPath;
  }
}

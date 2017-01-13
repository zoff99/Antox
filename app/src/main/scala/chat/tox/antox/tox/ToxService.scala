
package chat.tox.antox.tox

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.preference.PreferenceManager
import chat.tox.antox.av.CallService
import chat.tox.antox.callbacks.{AntoxOnSelfConnectionStatusCallback, ToxCallbackListener, ToxavCallbackListener}
import chat.tox.antox.data.State
import chat.tox.antox.utils.{AntoxLog, Options}
import im.tox.tox4j.core.enums.ToxConnection
import rx.lang.scala.schedulers.AndroidMainThreadScheduler
import rx.lang.scala.{Observable, Subscription}

import scala.concurrent.duration._

class ToxService extends Service {

  private var serviceThread: Thread = _

  private var keepRunning: Boolean = true

  private val connectionCheckInterval = 10000 // 10000 //in ms

  private val reconnectionIntervalSeconds = 120 // 60

  // 2 minutes in milliseconds
  private val BATTERY_SAVING_DELAY = 2 * 60 * 1000
  // how many normal loops to run in battery saving mode [0.5 secs now per loop]
  private val NORMAL_LOOPS = 120 * 2
  var isConnectedNow = false

  private var callService: CallService = _

  override def onCreate() {
    if (!ToxSingleton.isInited) {
      ToxSingleton.initTox(getApplicationContext)
      AntoxLog.debug("Initting ToxSingleton")
    }

    keepRunning = true
    val thisService = this

    val start = new Runnable() {

      override def run() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext)

        callService = new CallService(thisService)
        callService.start()

        val toxCallbackListener = new ToxCallbackListener(thisService)
        val toxAvCallbackListener = new ToxavCallbackListener(thisService)

        var reconnection: Subscription = null

        val connectionSubscription = AntoxOnSelfConnectionStatusCallback.connectionStatusSubject
          .observeOn(AndroidMainThreadScheduler())
          .distinctUntilChanged
          .subscribe(toxConnection => {
            if (toxConnection != ToxConnection.NONE) {
              if (reconnection != null && !reconnection.isUnsubscribed) {
                reconnection.unsubscribe()
              }
              isConnectedNow = true
              System.out.println("ToxService:" + "Tox Tox connected. Stopping reconnection")
              AntoxLog.debug("Tox connected. Stopping reconnection")
            } else {
              reconnection = Observable
                .interval(reconnectionIntervalSeconds seconds)
                .subscribe(x => {
                  AntoxLog.debug("Reconnecting")
                  ToxSingleton.bootstrap(getApplicationContext).subscribe()
                })
              isConnectedNow = false
              System.out.println("ToxService:" + "Tox disconnected. Scheduled reconnection every "+ reconnectionIntervalSeconds+ " seconds")
              AntoxLog.debug(s"Tox disconnected. Scheduled reconnection every $reconnectionIntervalSeconds seconds")
            }
          })

        var loops = 0
        var ticks = 0
        // val toxAv_interval_longer = ToxSingleton.toxAv.interval * 20
        val toxAv_interval_longer = 500 // 1 secs
        System.out.println("ToxService:div 1")
        // val toxCoreIterationRatio = Math.ceil(ToxSingleton.tox.interval / toxAv_interval_longer).toInt
        val toxCoreIterationRatio = 1
        System.out.println("ToxService:div 2")
        System.out.println("ToxService:" + "ToxSingleton.tox.interval = " + ToxSingleton.tox.interval)
        System.out.println("ToxService:" + "ToxSingleton.toxAv.interval = " + ToxSingleton.toxAv.interval)
        System.out.println("ToxService:" + "toxAv_interval_longer = " + toxAv_interval_longer)
        System.out.println("ToxService:" + "toxCoreIterationRatio = " + toxCoreIterationRatio)
        System.out.println("ToxService:" + "connectionCheckInterval = " + connectionCheckInterval)

        // --------------- main tox loop ---------------
        // --------------- main tox loop ---------------
        // --------------- main tox loop ---------------
        while (keepRunning) {
          if (!ToxSingleton.isToxConnected(preferences, thisService)) {
            try {
              Thread.sleep(connectionCheckInterval)
            } catch {
              case e: Exception =>
            }
          } else {
            try {
              if (Options.batterySavingMode) {
                loops = loops + 1
                if (loops > NORMAL_LOOPS) {
                  if (isConnectedNow) {
                    if (!State.transfers.isTransferring) {
                      loops = 0
                      try {
                        System.out.println("ToxService:" + "batterysavings=true will sleep for " + BATTERY_SAVING_DELAY + " ms")
                        Thread.sleep(BATTERY_SAVING_DELAY)
                      } catch {
                        case e: Exception =>
                      }
                    }
                    else
                      {
                        System.out.println("ToxService:" + "active filetransfers ...")
                      }
                  }
                  else {
                    System.out.println("ToxService:" + "batterysavings=true, want to go to sleep but not connected yet ...")
                  }
                }
              }

              if (ticks % toxCoreIterationRatio == 0) {
                System.out.println("ToxService:" + "ToxSingleton.tox.iterate")
                ToxSingleton.tox.iterate(toxCallbackListener)
              }
              System.out.println("ToxService:" + "ToxSingleton *+ toxAv +* iterate")
              ToxSingleton.toxAv.iterate(toxAvCallbackListener)

              val time = toxAv_interval_longer
              Thread.sleep(time)
              ticks += 1
            } catch {
              case e: Exception =>
                e.printStackTrace()
            }

          }
        }
        // --------------- main tox loop ---------------
        // --------------- main tox loop ---------------
        // --------------- main tox loop ---------------

        connectionSubscription.unsubscribe()
      }
    }

    serviceThread = new Thread(start)
    serviceThread.start()
  }

  override def onBind(intent: Intent): IBinder = null

  override def onStartCommand(intent: Intent, flags: Int, id: Int): Int = Service.START_STICKY

  override def onDestroy() {
    super.onDestroy()

    System.out.println("ToxService:" + "onDestroy(): enter")

    keepRunning = false

    System.out.println("ToxService:" + "onDestroy(): serviceThread.interrupt")
    serviceThread.interrupt()
    System.out.println("ToxService:" + "onDestroy(): serviceThread waiting to end ...")
    serviceThread.join()
    System.out.println("ToxService:" + "onDestroy(): serviceThread: ended")

    callService.destroy()

    ToxSingleton.save()
    ToxSingleton.isInited = false
    ToxSingleton.tox.close()
    AntoxLog.debug("onDestroy() called for Tox service")
    System.out.println("ToxService:" + "onDestroy(): ready")
  }
}

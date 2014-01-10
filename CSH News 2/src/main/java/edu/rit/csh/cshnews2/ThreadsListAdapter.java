/**
 See the NOTICE file
 distributed with this work for additional information
 regarding copyright ownership.  This code is licensed
 to you under the Apache License, Version 2.0 (the
 "License"); you may not use this file except in compliance
 with the License.  You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing,
 software distributed under the License is distributed on an
 "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 KIND, either express or implied.  See the License for the
 specific language governing permissions and limitations
 under the License.
 */
package edu.rit.csh.cshnews2;

import android.content.Context;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.Collection;
import java.util.List;

/**
 * The Adapter used for displaying the list of threads. This is used so that
 * unread threads can be bolded and marked different colors
 *
 * @param <T>
 */
class ThreadsListAdapter<T> extends ArrayAdapter<T> {
    private final Context context;

    public ThreadsListAdapter(Context context, int textViewResourceId,
                                     List<T> objects) {
        super(context, textViewResourceId, objects);
        this.context = context;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        String[] data = ((String[]) getItem(position));

        LayoutInflater infalInflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        convertView = infalInflater.inflate(R.layout.thread_item, null);
        TextView nameView = (TextView) convertView.findViewById(R.id.thread_name);
        TextView timeView = (TextView) convertView.findViewById(R.id.thread_date);
        TextView subjectView = (TextView) convertView.findViewById(R.id.thread_subject);

        nameView.setText(data[0]);
        timeView.setText(data[1].substring(11, 16)
                + " " + data[1].substring(5, 7)
                + "/" + data[1].substring(8, 10)
                + "/" + data[1].substring(0, 4));
        subjectView.setText(data[2]);

        if(data[3].equals("y"))
        {
            nameView.setTypeface(null, Typeface.BOLD);
            timeView.setTypeface(null, Typeface.BOLD);
            subjectView.setTypeface(null, Typeface.BOLD);
        }

        return convertView;
    }
}